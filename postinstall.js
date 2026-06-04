const fs = require('fs');
const path = require('path');

const modules = [
  'expo-asset',
  'expo-file-system',
  'expo-font',
  'expo-keep-awake',
];

const patchesDir = path.join(__dirname, 'patches');

modules.forEach((mod) => {
  const configPath = path.join(
    __dirname,
    'node_modules',
    'expo',
    'node_modules',
    mod,
    'expo-module.config.json'
  );
  const patchPath = path.join(
    patchesDir,
    `expo+node_modules+${mod}+0.0.0.patch`
  );

  if (!fs.existsSync(configPath)) {
    console.log(`[postinstall] SKIP ${mod}: config not found`);
    return;
  }

  const original = fs.readFileSync(configPath, 'utf-8');
  let config;
  try {
    config = JSON.parse(original);
  } catch {
    console.log(`[postinstall] SKIP ${mod}: invalid JSON`);
    return;
  }

  if (config.android && config.android.publication) {
    delete config.android.publication;
    fs.writeFileSync(configPath, JSON.stringify(config, null, 2) + '\n');
    console.log(`[postinstall] FIXED ${mod}: removed publication block`);
  } else {
    console.log(`[postinstall] OK ${mod}: no publication block`);
  }
});
