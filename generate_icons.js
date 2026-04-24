const sharp = require('sharp');
const path = require('path');
const fs = require('fs');

const inputIcon = path.join(__dirname, 'assets', 'icon.png');
const androidRes = path.join(__dirname, 'android', 'app', 'src', 'main', 'res');

const sizes = {
  'mdpi': 48,
  'hdpi': 72,
  'xhdpi': 96,
  'xxhdpi': 144,
  'xxxhdpi': 192
};

async function generate() {
  if (!fs.existsSync(inputIcon)) {
    console.error('icon.png not found');
    return;
  }
  for (const [density, size] of Object.entries(sizes)) {
    const dir = path.join(androidRes, `mipmap-${density}`);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    
    // Generar ic_launcher.png (normal y round en este caso usaremos el mismo para simplificar)
    await sharp(inputIcon)
      .resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b:0, alpha: 0 } })
      .toFile(path.join(dir, 'ic_launcher.png'));
      
    await sharp(inputIcon)
      .resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b:0, alpha: 0 } })
      .toFile(path.join(dir, 'ic_launcher_round.png'));
  }
  console.log('Iconos de Android generados exitosamente en los mipmap correspondientes.');
}
generate();
