export type ChoiceOption = { label: string; value: string };

export type Choice = {
  key: string;
  /** 按钮文案，例如「筛选 · 中文字幕」。 */
  text: string;
  value: string;
  options: ChoiceOption[];
  onChange: (value: string) => void;
  /** 有值时渲染成普通按钮（例如打开筛选面板），不弹菜单。 */
  onPress?: () => void;
};
