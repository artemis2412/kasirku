const fs = require("fs");
const path = require("path");
const sharp = require("sharp");
async function main() {
  const root = path.resolve(__dirname, "..");
  const svg = fs.readFileSync(path.join(root, "assets", "kasirku-mark.svg"));
  const resources = path.join(root, "resources");
  const icons = path.join(root, "icons");
  fs.mkdirSync(resources, { recursive: true });
  fs.mkdirSync(icons, { recursive: true });
  await sharp(svg).resize(1024, 1024, { fit: "contain" }).png().toFile(path.join(resources, "icon.png"));
  await sharp(svg).resize(1024, 1024, { fit: "contain" }).png().toFile(path.join(resources, "splash.png"));
  await sharp(svg).resize(192, 192, { fit: "contain" }).png().toFile(path.join(icons, "icon-192.png"));
  await sharp(svg).resize(512, 512, { fit: "contain" }).png().toFile(path.join(icons, "icon-512.png"));
  console.log("KASIRKU icon, PWA icons, and splash resources generated from approved logo SVG.");
}
main().catch(error => { console.error(error); process.exit(1); });
