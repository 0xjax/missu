export type ChoiceOption = { label: string; value: string };

export type Choice = {
  key: string;
  /** 按钮文案，例如「筛选 · 中文字幕」。 */
  text: string;
  value: string;
  options: ChoiceOption[];
  onChange: (value: string) => void;
};
