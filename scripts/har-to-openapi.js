import { mkdir, readFile, writeFile } from "node:fs/promises";

const types = (schema) =>
  schema.type === undefined
    ? []
    : Array.isArray(schema.type)
      ? schema.type
      : [schema.type];

function mergeSchemas(left, right) {
  const mergedTypes = [...new Set([...types(left), ...types(right)])];
  if (mergedTypes.length === 1 && mergedTypes[0] === "object") {
    const keys = [...new Set([
      ...Object.keys(left.properties ?? {}),
      ...Object.keys(right.properties ?? {}),
    ])];
    const properties = Object.fromEntries(keys.map((key) => {
      const a = left.properties?.[key];
      const b = right.properties?.[key];
      return [key, a && b ? mergeSchemas(a, b) : a ?? b ?? {}];
    }));
    const rightRequired = new Set(right.required ?? []);
    const required = (left.required ?? []).filter((key) => rightRequired.has(key));
    return { type: "object", properties, ...(required.length > 0 && { required }) };
  }
  if (mergedTypes.length === 1 && mergedTypes[0] === "array") {
    return {
      type: "array",
      items: left.items && right.items ? mergeSchemas(left.items, right.items) : left.items ?? right.items ?? {},
    };
  }
  return { type: mergedTypes.length === 1 ? mergedTypes[0] : mergedTypes.sort() };
}

function inferSchema(value) {
  if (value === null) return { type: "null" };
  if (Array.isArray(value)) {
    return {
      type: "array",
      items: value.length > 0 ? value.map(inferSchema).reduce(mergeSchemas) : {},
    };
  }
  if (typeof value === "object") {
    const entries = Object.entries(value);
    return {
      type: "object",
      properties: Object.fromEntries(entries.map(([key, item]) => [key, inferSchema(item)])),
      ...(entries.length > 0 && { required: entries.map(([key]) => key) }),
    };
  }
  if (typeof value === "number") return { type: Number.isInteger(value) ? "integer" : "number" };
  return { type: typeof value };
}

function inferQuerySchema(value) {
  if (/^-?\d+$/.test(value)) return { type: "integer" };
  if (/^-?\d+\.\d+$/.test(value)) return { type: "number" };
  if (value === "true" || value === "false") return { type: "boolean" };
  return { type: "string" };
}

function responseBody(entry) {
  const content = entry.response.content;
  if (!content?.text) return undefined;
  return content.encoding === "base64" ? Buffer.from(content.text, "base64").toString("utf8") : content.text;
}

function jsonSchema(text) {
  if (!text) return {};
  try {
    return inferSchema(JSON.parse(text));
  } catch {
    return { type: "string" };
  }
}

function operationId(method, path) {
  const words = `${method} ${path}`.replace(/[^a-zA-Z0-9]+/g, " ").trim().split(/\s+/);
  return words.map((word, index) => {
    const lower = word.toLowerCase();
    return index === 0 ? lower : `${lower[0]?.toUpperCase()}${lower.slice(1)}`;
  }).join("");
}

const inputs = process.argv.slice(2);
if (inputs.length === 0) {
  throw new Error("用法: bun scripts/har-to-openapi.js <har...>");
}

const operations = new Map();
for (const input of inputs) {
  const har = JSON.parse(await readFile(input, "utf8"));
  for (const entry of har.log?.entries ?? []) {
    const url = new URL(entry.request.url);
    if (url.protocol !== "http:" && url.protocol !== "https:") continue;

    const mimeType = entry.response.content?.mimeType?.split(";")[0].toLowerCase() || "application/octet-stream";
    if (mimeType !== "application/json" && !mimeType.endsWith("+json") && mimeType !== "text/html") continue;

    const method = entry.request.method.toUpperCase();
    const key = `${method} ${url.origin}${url.pathname}`;
    const operation = operations.get(key) ?? {
      method,
      origin: url.origin,
      path: url.pathname,
      samples: 0,
      query: new Map(),
      responses: new Map(),
    };
    operation.samples += 1;

    const queryNames = new Set(url.searchParams.keys());
    for (const name of queryNames) {
      const value = url.searchParams.get(name) ?? "";
      const previous = operation.query.get(name);
      operation.query.set(name, {
        samples: (previous?.samples ?? 0) + 1,
        schema: previous ? mergeSchemas(previous.schema, inferQuerySchema(value)) : inferQuerySchema(value),
      });
    }

    const postData = entry.request.postData;
    if (postData?.mimeType && postData.text) {
      const requestMimeType = postData.mimeType.split(";")[0];
      const schema = requestMimeType.includes("json") ? jsonSchema(postData.text) : { type: "string" };
      operation.requestBody = operation.requestBody
        ? { ...operation.requestBody, schema: mergeSchemas(operation.requestBody.schema, schema) }
        : { mimeType: requestMimeType, schema };
    }

    const status = entry.response.status;
    const response = operation.responses.get(status) ?? {
      description: entry.response.statusText || `HTTP ${status}`,
      content: new Map(),
    };
    const schema = mimeType === "text/html" ? { type: "string" } : jsonSchema(responseBody(entry));
    response.content.set(mimeType, response.content.has(mimeType)
      ? mergeSchemas(response.content.get(mimeType), schema)
      : schema);
    operation.responses.set(status, response);
    operations.set(key, operation);
  }
}

const servers = [...new Set([...operations.values()].map((operation) => operation.origin))]
  .map((url) => ({ url }));
const paths = {};
for (const operation of operations.values()) {
  paths[operation.path] ??= {};
  paths[operation.path][operation.method.toLowerCase()] = {
    operationId: operationId(operation.method, operation.path),
    summary: `${operation.method} ${operation.path}`,
    ...(operation.query.size > 0 && {
      parameters: [...operation.query].map(([name, query]) => ({
        name,
        in: "query",
        required: query.samples === operation.samples,
        schema: query.schema,
      })),
    }),
    ...(operation.requestBody && {
      requestBody: {
        required: true,
        content: { [operation.requestBody.mimeType]: { schema: operation.requestBody.schema } },
      },
    }),
    responses: Object.fromEntries([...operation.responses].map(([status, response]) => [status, {
      description: response.description,
      content: Object.fromEntries([...response.content].map(([mimeType, schema]) => [mimeType, { schema }])),
    }])),
  };
}

const document = {
  openapi: "3.1.0",
  info: {
    title: "Captured API",
    version: "0.1.0",
    description: "由脱敏 HAR 自动生成的草稿；路径参数、语义和认证方式需要人工校正。",
    license: { name: "UNLICENSED" },
  },
  servers,
  security: [],
  paths,
};

const output = "docs/api/openapi.generated.json";
await mkdir("docs/api", { recursive: true });
await writeFile(output, `${JSON.stringify(document, null, 2)}\n`);
console.log({ inputs: inputs.length, operations: operations.size, output });
