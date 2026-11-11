import { ScrollView, StyleSheet, View } from 'react-native';
import { Chip } from 'react-native-paper';

import type { VideoFilter, VideoSort } from '@/api/missav';
import { Radius, Spacing } from '@/constants/theme';
import { VIDEO_SORT_OPTIONS, type FilterOption } from '@/constants/video-options';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  filters: FilterOption[];
  filter?: VideoFilter;
  sort?: VideoSort;
  onFilterChange: (value?: VideoFilter) => void;
  onSortChange: (value?: VideoSort) => void;
};

export function VideoFilterBar({ filters, filter, sort, onFilterChange, onSortChange }: Props) {
  const theme = useTheme();

  return (
    <View style={styles.bar}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.choices}>
        {filters.map((item) => (
          <Chip key={item.label} compact selected={filter === item.value} onPress={() => onFilterChange(item.value)} style={[styles.choice, { backgroundColor: filter === item.value ? theme.accent : theme.backgroundElement }]} textStyle={{ color: filter === item.value ? theme.onAccent : theme.text }}>
            {item.label}
          </Chip>
        ))}
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.choices}>
        {VIDEO_SORT_OPTIONS.map((item) => (
          <Chip key={item.label} compact selected={sort === item.value} onPress={() => onSortChange(item.value)} style={[styles.choice, { backgroundColor: sort === item.value ? theme.accent : theme.backgroundElement }]} textStyle={{ color: sort === item.value ? theme.onAccent : theme.text }}>
            {item.label}
          </Chip>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    gap: Spacing.md,
  },
  choices: {
    gap: Spacing.sm,
  },
  choice: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
});
