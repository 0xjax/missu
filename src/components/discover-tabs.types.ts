export type DiscoverTabItem<T extends string> = { id: T; label: string };

export type DiscoverTabsProps<T extends string> = {
  items: DiscoverTabItem<T>[];
  value: T;
  onChange: (value: T) => void;
};
