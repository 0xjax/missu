import { createContext, useCallback, useContext, useState, type PropsWithChildren } from 'react';

import type { VideoSummary } from '@/api/missav';
import { VideoPreviewOverlay } from '@/components/video-preview';

type PreviewContextValue = {
  openPreview: (video: VideoSummary) => void;
};

const PreviewContext = createContext<PreviewContextValue | undefined>(undefined);

export function PreviewProvider({ children }: PropsWithChildren) {
  // NOTE: 全局只有一个预览实例：任何卡片长按都复用它，换片时旧播放器随组件卸载释放。
  const [video, setVideo] = useState<VideoSummary>();
  const openPreview = useCallback((next: VideoSummary) => setVideo(next), []);
  const close = useCallback(() => setVideo(undefined), []);

  return (
    <PreviewContext.Provider value={{ openPreview }}>
      {children}
      {video && <VideoPreviewOverlay onClose={close} video={video} />}
    </PreviewContext.Provider>
  );
}

export function useVideoPreview(): PreviewContextValue {
  const value = useContext(PreviewContext);
  if (!value) throw new Error('useVideoPreview 必须在 PreviewProvider 内使用');
  return value;
}
