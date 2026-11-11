import { useRouter, type Href } from 'expo-router';

import { CollapsibleGroup } from '@/components/collapsible-group';
import { SettingsRow } from '@/components/settings-row';
import { CHANNEL_GROUPS } from '@/constants/channel-groups';

/** 厂商 tab 顶部的策展频道组：默认折叠，点开进入对应列表页。 */
export function ChannelGroups() {
  const router = useRouter();

  return (
    <>
      {CHANNEL_GROUPS.map((group) => (
        <CollapsibleGroup key={group.id} title={group.title}>
          {group.channels.map((channel) => (
            <SettingsRow
              key={channel.path}
              label={channel.name}
              onPress={() => router.push({
                pathname: '/latest',
                params: { source: channel.path, title: channel.name, query: '' },
              } as unknown as Href)}
            />
          ))}
        </CollapsibleGroup>
      ))}
    </>
  );
}
