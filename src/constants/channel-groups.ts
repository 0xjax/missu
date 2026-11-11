export type Channel = { name: string; path: string };
export type ChannelGroup = { id: string; title: string; channels: Channel[] };

/**
 * 站点导航里的可展开频道组。它们都是标准列表页（支持筛选/排序/分页），
 * 属于厂商的精选子集；站点改版时只需改这里。
 */
export const CHANNEL_GROUPS: ChannelGroup[] = [
  {
    id: 'amateur',
    title: '素人',
    channels: [
      { name: 'SIRO', path: '/siro' },
      { name: 'LUXU', path: '/luxu' },
      { name: 'GANA', path: '/gana' },
      { name: 'PRESTIGE PREMIUM', path: '/maan' },
      { name: 'S-CUTE', path: '/scute' },
      { name: 'ARA', path: '/ara' },
    ],
  },
  {
    id: 'uncensored',
    title: '无码影片',
    channels: [
      { name: 'FC2', path: '/fc2' },
      { name: 'HEYZO', path: '/heyzo' },
      { name: '东京热', path: '/tokyohot' },
      { name: '一本道', path: '/1pondo' },
      { name: 'Caribbeancom', path: '/caribbeancom' },
      { name: 'Caribbeancompr', path: '/caribbeancompr' },
      { name: '10musume', path: '/10musume' },
      { name: 'pacopacomama', path: '/pacopacomama' },
      { name: 'Gachinco', path: '/gachinco' },
      { name: 'XXX-AV', path: '/xxxav' },
    ],
  },
  {
    id: 'asia',
    title: '亚洲 AV',
    channels: [
      { name: '人妻斩', path: '/marriedslash' },
      { name: '顽皮 4610', path: '/naughty4610' },
      { name: '顽皮 0930', path: '/naughty0930' },
      { name: '麻豆传媒', path: '/madou' },
      { name: 'TWAV', path: '/twav' },
      { name: 'Furuke', path: '/furuke' },
    ],
  },
];
