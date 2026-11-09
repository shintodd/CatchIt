const fs = require('fs');
const path = require('path');

const drawableDir = 'C:/Users/ainol/.gemini/antigravity/scratch/feedermy-android/app/src/main/res/drawable';

const icons = {
  ic_lucide_home: 'M 3,9 L 12,2 L 21,9 V 20 A 2,2 0 0 1 19,22 H 5 A 2,2 0 0 1 3,20 Z M 9,22 V 12 H 15 V 22',
  ic_lucide_locate: 'M 2,12 H 5 M 19,12 H 22 M 12,2 V 5 M 12,19 V 22 M 12,5 A 7,7 0 1 0 12,19 A 7,7 0 1 0 12,5 M 12,9 A 3,3 0 1 0 12,15 A 3,3 0 1 0 12,9',
  ic_lucide_map: 'M 3,6 L 9,3 L 15,6 L 21,3 V 18 L 15,21 L 9,18 L 3,21 Z M 9,3 V 18 M 15,6 V 21',
  ic_lucide_navigation: 'M 3,11 L 22,2 L 13,21 L 11,13 Z',
  ic_lucide_settings: 'M 12.22,2 H 11.78 A 2,2 0 0 0 9.78,4 V 4.18 A 2,2 0 0 1 8.78,5.91 L 8.35,6.16 A 2,2 0 0 1 6.35,6.16 L 6.2,6.08 A 2,2 0 0 0 3.47,6.81 L 3.25,7.19 A 2,2 0 0 0 3.98,9.92 L 4.13,10.02 A 2,2 0 0 1 5.13,11.74 V 12.25 A 2,2 0 0 1 4.13,13.99 L 3.98,14.08 A 2,2 0 0 0 3.25,16.81 L 3.47,17.19 A 2,2 0 0 0 6.2,17.92 L 6.35,17.84 A 2,2 0 0 1 8.35,17.84 L 8.78,18.09 A 2,2 0 0 1 9.78,19.82 V 20 A 2,2 0 0 0 11.78,22 H 12.22 A 2,2 0 0 0 14.22,20 V 19.82 A 2,2 0 0 1 15.22,18.09 L 15.65,17.84 A 2,2 0 0 1 17.65,17.84 L 17.8,17.92 A 2,2 0 0 0 20.53,17.19 L 20.75,16.81 A 2,2 0 0 0 20.02,14.08 L 19.87,13.99 A 2,2 0 0 1 18.87,12.25 V 11.74 A 2,2 0 0 1 19.87,10.02 L 20.02,9.92 A 2,2 0 0 0 20.75,7.19 L 20.53,6.81 A 2,2 0 0 0 17.8,6.08 L 17.65,6.16 A 2,2 0 0 1 15.65,6.16 L 15.22,5.91 A 2,2 0 0 1 14.22,4.18 V 4 A 2,2 0 0 0 12.22,2 Z M 12,9 A 3,3 0 1 0 12,15 A 3,3 0 1 0 12,9',
  ic_lucide_bell: 'M 6,8 A 6,6 0 0 1 18,8 C 18,15 21,17 21,17 H 3 C 3,17 6,15 6,8 Z M 10.3,21 A 1.94,1.94 0 0 0 13.7,21',
  ic_lucide_rotate_cw: 'M 21,12 A 9,9 0 1 1 12,3 C 14.52,3 16.85,3.83 18.72,5.24 L 21,8 M 21,3 V 8 H 16',
  ic_lucide_bus: 'M 8,6 V 12 M 15,6 V 12 M 2,12 H 21.6 M 18,18 H 21 C 21,18 21.5,16.3 21.8,15.2 C 21.9,14.8 22,14.4 22,14 C 22,13.6 21.9,13.2 21.8,12.8 C 21.5,11.7 21,10 21,10 H 4 C 4,10 3.5,11.7 3.2,12.8 C 3.1,13.2 3,13.6 3,14 C 3,14.4 3.1,14.8 3.2,15.2 C 3.5,16.3 4,18 4,18 H 7 M 7,16 A 2,2 0 1 0 7,20 A 2,2 0 1 0 7,16 M 17,16 A 2,2 0 1 0 17,20 A 2,2 0 1 0 17,16',
  ic_lucide_map_pin: 'M 20,10 C 20,14.993 14.461,20.193 12.601,21.799 A 1,1 0 0 1 11.399,21.799 C 9.539,20.193 4,14.993 4,10 A 8,8 0 0 1 20,10 Z M 12,7 A 3,3 0 1 0 12,13 A 3,3 0 1 0 12,7',
  ic_lucide_chevron_down: 'M 6,9 L 12,15 L 18,9',
  ic_lucide_x: 'M 18,6 L 6,18 M 6,6 L 18,18',
  ic_lucide_arrow_down_up: 'M 3,16 L 7,20 L 11,16 M 7,20 V 4 M 21,8 L 17,4 L 13,8 M 17,4 V 20',
  ic_lucide_radio: 'M 4.9,19.1 C 1,15.2 1,8.8 4.9,4.9 M 7.8,16.2 C 5.5,13.9 5.5,10.1 7.8,7.7 M 12,10 A 2,2 0 1 0 12,14 A 2,2 0 1 0 12,10 M 16.2,7.8 C 18.5,10.1 18.5,13.9 16.2,16.3 M 19.1,4.9 C 23,8.8 23,15.1 19.1,19',
  ic_lucide_search: 'M 11,3 A 8,8 0 1 0 11,19 A 8,8 0 1 0 11,3 M 21,21 L 16.65,16.65',
  ic_lucide_arrow_left: 'M 19,12 H 5 M 12,19 L 5,12 L 12,5',
  ic_lucide_check: 'M 20,6 L 9,17 L 4,12',
  ic_lucide_zap: 'M 13,2 L 3,14 H 12 L 11,22 L 21,10 H 12 Z',
  ic_lucide_globe: 'M 12,2 A 10,10 0 1 0 12,22 A 10,10 0 1 0 12,2 M 2,12 H 22 M 12,2 C 16,6.4 16,17.6 12,22 M 12,2 C 8,6.4 8,17.6 12,22',
  ic_lucide_download: 'M 21,15 V 19 A 2,2 0 0 1 19,21 H 5 A 2,2 0 0 1 3,19 V 15 M 7,10 L 12,15 L 17,10 M 12,15 V 3'
};

for (const [name, pathData] of Object.entries(icons)) {
  const xml = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="${pathData}"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
`;
  fs.writeFileSync(path.join(drawableDir, `${name}.xml`), xml, 'utf8');
}

console.log(`Generated ${Object.keys(icons).length} Lucide vector drawables!`);
