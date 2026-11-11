import { Image } from 'expo-image';
import * as SplashScreen from 'expo-splash-screen';
import { useState } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import Animated, { Easing, Keyframe, useReducedMotion } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

const DURATION = 600;

export function AnimatedSplashOverlay() {
  const [animate, setAnimate] = useState(false);
  const [visible, setVisible] = useState(true);
  const reducedMotion = useReducedMotion();
  const { height } = useWindowDimensions();

  if (!visible) return null;

  const initialScale = height / 90;
  const splashKeyframe = new Keyframe({
    0: {
      opacity: 1,
      transform: [{ scale: initialScale }],
    },
    70: {
      opacity: 0,
      easing: Easing.bezier(0.23, 1, 0.32, 1),
    },
    100: {
      opacity: 0,
      transform: [{ scale: 1 }],
      easing: Easing.bezier(0.23, 1, 0.32, 1),
    },
  });

  const image = <Image style={styles.image} source={require('@/assets/images/splash-icon.png')} />;

  return animate ? (
    <Animated.View
      entering={splashKeyframe.duration(DURATION).withCallback((finished) => {
        'worklet';
        if (finished) {
          scheduleOnRN(setVisible, false);
        }
      })}
      style={styles.splashOverlay}>
      {image}
    </Animated.View>
  ) : (
    <View
      onLayout={() => {
        SplashScreen.hideAsync().finally(() => {
          if (reducedMotion) {
            setVisible(false);
          } else {
            setAnimate(true);
          }
        });
      }}
      style={styles.splashOverlay}>
      {image}
    </View>
  );
}

const styles = StyleSheet.create({
  image: {
    width: 76,
    height: 71,
  },
  splashOverlay: {
    ...StyleSheet.absoluteFill,
    // NOTE: 与 app.json 的原生启动背景保持一致。
    backgroundColor: '#208AEF',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
});
