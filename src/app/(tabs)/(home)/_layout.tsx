import { Slot } from 'expo-router';

// NOTE: tab 内不嵌原生 Stack，标题栏由页面渲染（见 components/tab-header.tsx）。
export default function HomeTabLayout() {
  return <Slot />;
}
