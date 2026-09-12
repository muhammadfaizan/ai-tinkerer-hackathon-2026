import { NativeModule, requireOptionalNativeModule } from 'expo';

export type RecentUsage = {
  packageName: string;
  appLabel: string;
  durationMin: number;
};

declare class NativeUsageStatsModule extends NativeModule<{}> {
  hasUsageAccess(): Promise<boolean>;
  openUsageAccessSettings(): void;
  getRecentUsage(minutesBack: number): Promise<RecentUsage | null>;
}

const nativeModule = requireOptionalNativeModule<NativeUsageStatsModule>('UsageStatsModule');

// This stays safe in Expo Go: the native module exists only in an Android dev build.
const UsageStatsModule = {
  isAvailable: Boolean(nativeModule),
  hasUsageAccess: () => nativeModule?.hasUsageAccess() ?? Promise.resolve(false),
  openUsageAccessSettings: () => nativeModule?.openUsageAccessSettings(),
  getRecentUsage: (minutesBack: number) => nativeModule?.getRecentUsage(minutesBack) ?? Promise.resolve(null),
};

export default UsageStatsModule;
