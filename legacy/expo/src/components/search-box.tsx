import { useState } from 'react';
import { Keyboard, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Pill } from '@/components/ui/pill';
import { SearchField } from '@/components/ui/search-field';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import {
  addSearchHistory,
  clearSearchHistory,
  getSearchHistory,
} from '@/storage/search-history';

type Props = {
  value: string;
  onChangeText: (value: string) => void;
  onSearch: (value: string) => void;
};

export function SearchBox({ value, onChangeText, onSearch }: Props) {
  const theme = useTheme();
  const [focused, setFocused] = useState(false);
  const [history, setHistory] = useState<string[]>([]);

  const submit = (keyword: string) => {
    const normalized = keyword.trim();
    if (!normalized) return;
    void addSearchHistory(normalized).then(setHistory);
    onSearch(normalized);
    setFocused(false);
    Keyboard.dismiss();
  };

  return (
    <View>
      <SearchField
        accessibilityLabel="搜索视频"
        onBlur={() => setFocused(false)}
        onChangeText={onChangeText}
        onFocus={() => {
          setFocused(true);
          void getSearchHistory().then(setHistory);
        }}
        onSubmit={submit}
        placeholder="搜索视频"
        value={value}
      />
      {focused && history.length > 0 && (
        <View style={[styles.history, { backgroundColor: theme.backgroundElement }]}>
          <View style={styles.historyHeader}>
            <ThemedText type="smallBold">最近搜索</ThemedText>
            <Pressable accessibilityRole="button" hitSlop={8} onPress={() => {
              void clearSearchHistory().then(() => setHistory([]));
            }}>
              <ThemedText type="small" themeColor="textSecondary">清除</ThemedText>
            </Pressable>
          </View>
          <View style={styles.historyItems}>
            {history.map((keyword) => (
              <Pill
                key={keyword}
                label={keyword}
                onPress={() => {
                  onChangeText(keyword);
                  submit(keyword);
                }}
              />
            ))}
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  history: {
    borderRadius: Radius.md,
    gap: Spacing.md,
    marginTop: Spacing.sm,
    padding: Spacing.md,
  },
  historyHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  historyItems: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
});
