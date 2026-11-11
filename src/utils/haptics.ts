import * as Haptics from 'expo-haptics';

function ignoreFailure(promise: Promise<void>): void {
  void promise.catch(() => {});
}

export function selectionHaptic(): void {
  ignoreFailure(Haptics.selectionAsync());
}

export function impactHaptic(style: Haptics.ImpactFeedbackStyle = Haptics.ImpactFeedbackStyle.Light): void {
  ignoreFailure(Haptics.impactAsync(style));
}

export function notificationHaptic(type: Haptics.NotificationFeedbackType): void {
  ignoreFailure(Haptics.notificationAsync(type));
}

export function successHaptic(): void {
  notificationHaptic(Haptics.NotificationFeedbackType.Success);
}

export function errorHaptic(): void {
  notificationHaptic(Haptics.NotificationFeedbackType.Error);
}
