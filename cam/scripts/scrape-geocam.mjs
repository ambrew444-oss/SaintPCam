const BASE_URL = "https://www.geocam.ru";
const CITY_PATH = "/in/st-petersburg/";
import { mkdir, writeFile } from "node:fs/promises";

function decodeHtml(value) {
  return String(value || "")
    .replace(/&nbsp;/g, " ")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&")
    .replace(/&hellip;/g, "...")
    .replace(/&ndash;/g, "-")
    .replace(/&mdash;/g, "-")
    .replace(/&laquo;/g, "\"")
    .replace(/&raquo;/g, "\"")
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function slugToId(pathname) {
  return pathname
    .replace(/^\/online\//, "")
    .replace(/\/$/, "")
    .replace(/[^a-z0-9-]+/gi, "-")
    .replace(/-+/g, "-");
}

function normalizeUrl(rawUrl) {
  if (!rawUrl) {
    return "";
  }

  const decoded = rawUrl.replace(/&amp;/g, "&").trim();
  if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
    return decoded;
  }
  if (decoded.startsWith("//")) {
    return `https:${decoded}`;
  }
  if (decoded.startsWith("/")) {
    return new URL(decoded, BASE_URL).toString();
  }
  return decoded;
}

function normalizeSourceUrl(rawUrl) {
  if (!rawUrl) {
    return "";
  }

  const decoded = rawUrl.replace(/&amp;/g, "&").trim();
  if (decoded.startsWith("/away/?")) {
    return decodeURIComponent(decoded.replace("/away/?", ""));
  }
  return normalizeUrl(decoded);
}

function inferProvider(embedUrl) {
  if (embedUrl.includes("vkvideo.ru")) {
    return "VK Video";
  }
  if (embedUrl.includes("rutube.ru")) {
    return "Rutube";
  }
  if (embedUrl.includes("camera.rt.ru") || embedUrl.includes("lk-b2b.camera.rt.ru")) {
    return "RT";
  }
  if (embedUrl.includes("ivideon.com")) {
    return "Ivideon";
  }
  if (embedUrl.includes("timetechnology.ru") || embedUrl.includes("lakhta-app.timetechnology.ru")) {
    return "TimeTechnology";
  }
  if (embedUrl.includes("twitch.tv")) {
    return "Twitch";
  }
  if (embedUrl.includes("youtube.com") || embedUrl.includes("youtube-nocookie.com")) {
    return "YouTube";
  }
  return new URL(embedUrl).hostname.replace(/^www\./, "");
}

function buildDistrictsMap(html) {
  const districts = new Map();
  const regex = /<option value="(\/in\/[^"]+\/)">›\s*([^<(]+?)\s*\((\d+)\)<\/option>/g;
  let match;
  while ((match = regex.exec(html)) !== null) {
    districts.set(match[2].trim(), match[1]);
  }
  return districts;
}

function extractListingLinks(html) {
  const links = [];
  const regex = /<a href="(\/online\/[^"]+\/)" rel="bookmark"[^>]*>[\s\S]*?<b>([^<]+)<\/b>/g;
  let match;
  while ((match = regex.exec(html)) !== null) {
    links.push({
      path: match[1],
      title: decodeHtml(match[2])
    });
  }
  return links;
}

function cleanDistrict(value) {
  return decodeHtml(value || "")
    .replace(/^Онлайн веб-камеры\s+/i, "")
    .replace(/\s+Санкт-Петербурга$/i, "")
    .trim();
}

async function fetchHtml(url) {
  const response = await fetch(url, {
    headers: {
      "user-agent": "Mozilla/5.0 (compatible; Codex camera scraper/1.0)"
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch ${url}: ${response.status}`);
  }

  return response.text();
}

function parseCameraPage(path, html, listingTitle) {
  const title =
    listingTitle ||
    decodeHtml(html.match(/<h1>([^<]+)<\/h1>/)?.[1]) ||
    decodeHtml(html.match(/"name":"([^"]+)"/)?.[1]) ||
    slugToId(path);

  const description =
    decodeHtml(html.match(/<p class="news-desc">([\s\S]*?)<\/p>/)?.[1]) ||
    decodeHtml(html.match(/<div class="page description"[^>]*>([\s\S]*?)<\/div>/)?.[1]);

  const district =
    cleanDistrict(
      [...html.matchAll(/<span class="campath">[\s\S]*?<a[^>]+href="\/in\/[^"]+\/">([^<]+)<\/a>\s*<\/span>/g)]
        .map((item) => item[1])
        .at(-1)
    ) || "Санкт-Петербург";

  const iframeMatch = html.match(/<iframe[^>]+src="([^"]+)"/i);
  const embedUrl = normalizeUrl(iframeMatch?.[1] || "");

  const sourceMatch = html.match(/<div class="cam_source">Источник:\s*<a href="([^"]+)"[^>]*>([^<]+)<\/a>/i);
  const sourceUrl = normalizeSourceUrl(sourceMatch?.[1] || "");
  const sourceName = decodeHtml(sourceMatch?.[2] || inferProvider(embedUrl));

  const mapMatch = html.match(/geoMapEmbed\("([0-9.\-]+),([0-9.\-]+),[0-9]+"\)/);
  const lat = mapMatch ? Number(mapMatch[1]) : null;
  const lng = mapMatch ? Number(mapMatch[2]) : null;

  const categories = [...html.matchAll(/<a href="\/in\/all\/[^"]+\/"[^>]*><nobr>([^<]+)<\/nobr><\/a>/g)]
    .map((item) => decodeHtml(item[1]))
    .filter(Boolean);

  const statusLabel =
    decodeHtml(html.match(/<div class="status">Статус:<br><b>[\s\S]*?<\/span>\s*([^<]+)<\/b>/)?.[1]) || "";
  const statusText =
    decodeHtml(html.match(/<div class="status">Статус:<br><b>[\s\S]*?<\/b><i>\(([^<]+)\)<\/i>/)?.[1]) || "";

  return {
    id: slugToId(path),
    pagePath: path,
    name: title.replace(/\s*,\s*Санкт-Петербург$/i, "").trim(),
    district,
    location: district,
    lat,
    lng,
    sourceName: `Geocam / ${sourceName}`,
    sourceUrl: sourceUrl || new URL(path, BASE_URL).toString(),
    embedUrl,
    provider: inferProvider(embedUrl),
    tags: [...new Set([district.toLowerCase(), ...categories.slice(0, 3)])],
    description,
    statusLabel,
    accessNote: statusLabel || statusText
      ? `Статус источника по данным Geocam: ${[statusLabel, statusText].filter(Boolean).join(", ")}.`
      : "Публичная трансляция из открытого интернет-источника."
  };
}

function filterSupported(cameras) {
  const filtered = cameras.filter((camera) => {
    if (!camera.embedUrl || !camera.lat || !camera.lng) {
      return false;
    }

    return !camera.embedUrl.includes("player.twitch.tv")
      && !camera.embedUrl.includes("youtube.com")
      && !camera.embedUrl.includes("youtube-nocookie.com")
      && !/нестабиль|не работает/i.test(camera.statusLabel);
  });

  const unique = new Map();
  filtered.forEach((camera) => {
    if (!unique.has(camera.embedUrl)) {
      unique.set(camera.embedUrl, camera);
    }
  });

  return [...unique.values()];
}

function serializeCamera(camera) {
  return `  {
    id: ${JSON.stringify(camera.id)},
    name: ${JSON.stringify(camera.name)},
    district: ${JSON.stringify(camera.district)},
    location: ${JSON.stringify(camera.location)},
    lat: ${camera.lat},
    lng: ${camera.lng},
    sourceName: ${JSON.stringify(camera.sourceName)},
    sourceUrl: ${JSON.stringify(camera.sourceUrl)},
    embedUrl: ${JSON.stringify(camera.embedUrl)},
    tags: ${JSON.stringify(camera.tags)},
    description: ${JSON.stringify(camera.description)},
    accessNote: ${JSON.stringify(camera.accessNote)}
  }`;
}

async function main() {
  const firstPageHtml = await fetchHtml(new URL(CITY_PATH, BASE_URL));
  const pageCount = Number(firstPageHtml.match(/<b>Страница 1<\/b> из (\d+)/)?.[1] || 1);

  const listingMap = new Map();

  for (let page = 1; page <= pageCount; page += 1) {
    const path = page === 1 ? CITY_PATH : `${CITY_PATH}${page}/`;
    const html = page === 1 ? firstPageHtml : await fetchHtml(new URL(path, BASE_URL));
    extractListingLinks(html).forEach((link) => {
      if (!listingMap.has(link.path)) {
        listingMap.set(link.path, link.title);
      }
    });
  }

  const cameras = [];

  for (const [path, listingTitle] of listingMap.entries()) {
    try {
      const html = await fetchHtml(new URL(path, BASE_URL));
      cameras.push(parseCameraPage(path, html, listingTitle));
    } catch (error) {
      console.error(`Failed to parse ${path}:`, error.message);
    }
  }

  const supported = filterSupported(cameras).sort((a, b) => a.name.localeCompare(b.name, "ru"));
  const providers = supported.reduce((acc, camera) => {
    acc[camera.provider] = (acc[camera.provider] || 0) + 1;
    return acc;
  }, {});

  const output = `window.CAMERA_DATA = [\n${supported.map(serializeCamera).join(",\n")}\n];\n`;
  const report = {
    totalListingEntries: listingMap.size,
    supportedEntries: supported.length,
    providers
  };

  await mkdir("generated", { recursive: true });
  await writeFile("generated/cameras.generated.js", output, "utf8");
  await writeFile("generated/cameras.report.json", JSON.stringify(report, null, 2), "utf8");

  console.log(JSON.stringify(report, null, 2));
}

await main();
