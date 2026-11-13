import { Slot } from 'expo-router';

// NOTE: tab 内不嵌原生 Stack。原生 Stack 在转场中会把内容高度传给 tab 宿主，快速连按
// 返回时底部栏会短暂落到底部内容末尾（约 2 秒自愈）。标题栏由页面自己渲染 TabHeader。
export default function AccountTabLayout() {
  return <Slot />;
}