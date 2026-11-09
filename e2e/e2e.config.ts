import type { E2EConfig } from 'e2e';
import { mobile } from '@e2e-dev/mobile';

/**
 * CatchIt E2E suite.
 *
 * Runs against the Android emulator that is already booted on emulator-5554.
 * The app is built and installed separately (`./gradlew installDebug`), and
 * the engine launches the installed package.
 */
export default {
  targets: [
    {
      name: 'android',
      engine: mobile({ platform: 'android', device: 'emulator-5554' }),
      app: {
        bundleId: 'my.feeder.app',
        // The APK produced by the Gradle build.
        appPath: '../app/build/outputs/apk/debug/app-debug.apk',
      },
    },
  ],
  workers: 1,
} satisfies E2EConfig;
