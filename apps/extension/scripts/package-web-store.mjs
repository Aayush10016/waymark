import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";

const packageVersion = "0.1.0";
const distRoot = "dist";
const artifactName = `trail-extension-${packageVersion}`;
const packagePath = join(distRoot, `${artifactName}.zip`);
const checksumPath = join(distRoot, `${artifactName}.sha256`);

const crcTable = new Uint32Array(256);
for (let i = 0; i < 256; i += 1) {
  let value = i;
  for (let bit = 0; bit < 8; bit += 1) {
    value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  }
  crcTable[i] = value >>> 0;
}

function crc32(buffer) {
  let value = 0xffffffff;
  for (const byte of buffer) {
    value = crcTable[(value ^ byte) & 0xff] ^ (value >>> 8);
  }
  return (value ^ 0xffffffff) >>> 0;
}

function uint16(value) {
  const buffer = Buffer.alloc(2);
  buffer.writeUInt16LE(value);
  return buffer;
}

function uint32(value) {
  const buffer = Buffer.alloc(4);
  buffer.writeUInt32LE(value);
  return buffer;
}

function collectFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const absolutePath = join(directory, entry.name);
    if (absolutePath === packagePath || absolutePath === checksumPath) {
      continue;
    }
    if (entry.isDirectory()) {
      files.push(...collectFiles(absolutePath));
    } else if (entry.isFile()) {
      files.push(absolutePath);
    }
  }
  return files.sort((left, right) => relative(distRoot, left).localeCompare(relative(distRoot, right)));
}

function createZip(files) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  for (const file of files) {
    const fileName = Buffer.from(relative(distRoot, file).replaceAll("\\", "/"));
    const data = readFileSync(file);
    const checksum = crc32(data);
    const localHeader = Buffer.concat([
      uint32(0x04034b50),
      uint16(20),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(checksum),
      uint32(data.length),
      uint32(data.length),
      uint16(fileName.length),
      uint16(0),
      fileName
    ]);
    localParts.push(localHeader, data);

    centralParts.push(Buffer.concat([
      uint32(0x02014b50),
      uint16(20),
      uint16(20),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(checksum),
      uint32(data.length),
      uint32(data.length),
      uint16(fileName.length),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(0),
      uint32(offset),
      fileName
    ]));

    offset += localHeader.length + data.length;
  }

  const centralDirectory = Buffer.concat(centralParts);
  const end = Buffer.concat([
    uint32(0x06054b50),
    uint16(0),
    uint16(0),
    uint16(files.length),
    uint16(files.length),
    uint32(centralDirectory.length),
    uint32(offset),
    uint16(0)
  ]);

  return Buffer.concat([...localParts, centralDirectory, end]);
}

if (!existsSync(join(distRoot, "manifest.json"))) {
  throw new Error("Extension dist is missing. Run the extension build before packaging.");
}

const files = collectFiles(distRoot);
for (const file of files) {
  if (file.endsWith(".map")) {
    throw new Error(`Source map is not allowed in Web Store package: ${file}`);
  }
  if (statSync(file).size === 0) {
    throw new Error(`Empty package file is not allowed: ${file}`);
  }
}

const zip = createZip(files);
writeFileSync(packagePath, zip);
writeFileSync(checksumPath, `${createHash("sha256").update(zip).digest("hex")}  ${packagePath.replaceAll("\\", "/")}\n`);

console.log(`Created ${packagePath}`);
