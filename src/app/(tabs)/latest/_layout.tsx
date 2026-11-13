import { Slot } from 'expo-router';

// NOTE: tab 内不嵌原生 Stack，标题栏由页面用 TabHeader 自绘（见 components/tab-header.tsx）。
export default function LatestTabLayout() {
  return <Slot />;
}
