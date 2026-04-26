(function () {
  const cityCatalog = {
    spb: { countryKey: "russia", country: "Россия", city: "Санкт-Петербург", center: [59.93863, 30.31413], zoom: 11 },
    moscow: { countryKey: "russia", country: "Россия", city: "Москва", center: [55.75586, 37.6177], zoom: 11 },
    newyork: { countryKey: "usa", country: "США", city: "Нью-Йорк", center: [40.7128, -74.006], zoom: 11 },
    miami: { countryKey: "usa", country: "США", city: "Майами", center: [25.7617, -80.1918], zoom: 11 },
    london: { countryKey: "uk", country: "Великобритания", city: "Лондон", center: [51.5074, -0.1278], zoom: 11 },
    manchester: { countryKey: "uk", country: "Великобритания", city: "Манчестер", center: [53.4808, -2.2426], zoom: 11 }
  };

  const countryCatalog = {
    russia: {
      label: "Россия",
      center: [61, 96],
      zoom: 4,
      bounds: [
        [41.2, 19.0],
        [81.9, 191.0]
      ]
    },
    usa: {
      label: "США",
      center: [39.8, -98.5],
      zoom: 4,
      bounds: [
        [24.3, -125.0],
        [49.5, -66.7]
      ]
    },
    uk: {
      label: "Великобритания",
      center: [54.5, -2.2],
      zoom: 6,
      bounds: [
        [49.8, -8.8],
        [59.2, 2.1]
      ]
    }
  };

  const countryShapes = {
    usa: [
      [49.38, -124.8],
      [47.2, -124.0],
      [45.8, -123.0],
      [41.9, -124.3],
      [37.2, -122.6],
      [32.5, -117.2],
      [31.2, -111.1],
      [29.1, -105.0],
      [26.1, -97.1],
      [29.0, -90.0],
      [30.1, -84.0],
      [25.0, -80.1],
      [30.7, -81.0],
      [35.0, -76.0],
      [40.2, -73.5],
      [44.8, -67.1],
      [47.8, -69.0],
      [49.38, -95.2]
    ],
    uk: [
      [50.0, -5.8],
      [50.9, -4.8],
      [51.5, -3.1],
      [52.2, -4.0],
      [53.0, -3.2],
      [54.0, -3.3],
      [55.0, -4.7],
      [56.0, -5.4],
      [57.3, -4.3],
      [58.5, -3.3],
      [57.7, -1.7],
      [56.6, -2.1],
      [55.5, -1.9],
      [54.7, -1.0],
      [53.7, -0.8],
      [52.8, 0.8],
      [51.8, 1.5],
      [50.8, 0.6],
      [50.0, -1.4]
    ],
    russia: [
      [54.0, 19.0],
      [57.5, 28.0],
      [59.8, 31.0],
      [61.0, 40.0],
      [63.0, 52.0],
      [66.0, 66.0],
      [69.0, 85.0],
      [72.0, 105.0],
      [72.5, 128.0],
      [71.0, 150.0],
      [67.0, 171.0],
      [62.0, 178.0],
      [57.0, 160.0],
      [52.0, 142.0],
      [47.0, 132.0],
      [45.0, 120.0],
      [46.0, 105.0],
      [50.0, 90.0],
      [51.5, 74.0],
      [50.0, 60.0],
      [48.5, 46.0],
      [45.0, 37.0],
      [47.0, 30.0]
    ]
  };

  const cityStreamTemplates = {
    newyork: {
      sourceName: "EarthCam / SkylineWebcams",
      sourceUrls: [
        "https://www.earthcam.com/usa/newyork/timessquare/",
        "https://www.skylinewebcams.com/en/webcam/united-states/new-york/new-york/new-york-city.html",
        "https://www.skylinewebcams.com/en/webcam/united-states/new-york/new-york/times-square.html",
        "https://www.skylinewebcams.com/en/webcam/united-states/new-york/new-york/5th-avenue.html",
        "https://www.skylinewebcams.com/en/webcam/united-states/new-york/new-york/rockefeller-center.html"
      ],
      embedUrls: [
        "https://www.youtube.com/embed/rnXIjl_Rzy4",
        "https://www.youtube.com/embed/QTTTY_ra2Tg"
      ],
      anchors: [
        { name: "Times Square", lat: 40.758, lng: -73.9855 },
        { name: "5th Avenue", lat: 40.7618, lng: -73.9754 },
        { name: "Rockefeller Center", lat: 40.7587, lng: -73.9787 },
        { name: "Bryant Park", lat: 40.7536, lng: -73.9832 }
      ]
    },
    miami: {
      sourceName: "EarthCam",
      sourceUrls: [
        "https://www.earthcam.com/usa/florida/miamiandthebeaches/",
        "https://www.earthcam.com/usa/florida/miami/southbeach/",
        "https://www.earthcam.com/usa/florida/miami/wynwood/"
      ],
      embedUrls: [
        "https://www.youtube.com/embed/iEqXPG17xnw"
      ],
      anchors: [
        { name: "South Beach", lat: 25.7826, lng: -80.1341 },
        { name: "Ocean Drive", lat: 25.7766, lng: -80.1321 },
        { name: "Downtown Miami", lat: 25.7743, lng: -80.1937 },
        { name: "Wynwood", lat: 25.8004, lng: -80.1992 }
      ]
    },
    london: {
      sourceName: "SkylineWebcams / WorldCam",
      sourceUrls: [
        "https://www.skylinewebcams.com/en/webcam/united-kingdom/england/london/abbey-road.html",
        "https://www.skylinewebcams.com/en/webcam/united-kingdom/england/london/london-skyline.html",
        "https://www.skylinewebcams.com/en/webcam/united-kingdom/england/london/tower-bridge.html",
        "https://worldcam.eu/"
      ],
      embedUrls: [
        "https://www.youtube.com/embed/M3EYAY2MftI",
        "https://www.youtube.com/embed/8JCk5M_xrBs"
      ],
      anchors: [
        { name: "Westminster", lat: 51.5007, lng: -0.1246 },
        { name: "Tower Bridge", lat: 51.5055, lng: -0.0754 },
        { name: "Abbey Road", lat: 51.5321, lng: -0.1772 },
        { name: "City of London", lat: 51.5138, lng: -0.0918 }
      ]
    },
    manchester: {
      sourceName: "SkylineWebcams / WorldCam",
      sourceUrls: [
        "https://www.skylinewebcams.com/en/webcam/united-kingdom/england/manchester/deansgate.html",
        "https://worldcam.eu/webcams/united-kingdom/england/manchester/",
        "https://worldcam.eu/"
      ],
      embedUrls: [
        "https://www.youtube.com/embed/RVqaMCty3-Q",
        "https://www.youtube.com/embed/Na96A_n4yZs"
      ],
      anchors: [
        { name: "Deansgate", lat: 53.4774, lng: -2.2505 },
        { name: "Piccadilly", lat: 53.4771, lng: -2.2308 },
        { name: "Northern Quarter", lat: 53.4839, lng: -2.2365 },
        { name: "Manchester Arena", lat: 53.4881, lng: -2.2436 }
      ]
    }
  };

  function createSyntheticCity(cityKey, baseLat, baseLng, count) {
    const stream = cityStreamTemplates[cityKey];
    const city = cityCatalog[cityKey].city;
    const list = [];
    for (let i = 1; i <= count; i += 1) {
      const anchor = stream.anchors[(i - 1) % stream.anchors.length];
      list.push({
        id: `${cityKey}-${i}`,
        name: `${anchor.name} • камера ${i}`,
        district: city,
        location: `${city}, ${anchor.name}`,
        lat: anchor.lat,
        lng: anchor.lng,
        sourceName: stream.sourceName,
        sourceUrl: stream.sourceUrls[(i - 1) % stream.sourceUrls.length],
        embedUrl: stream.embedUrls[(i - 1) % stream.embedUrls.length],
        tags: [cityKey, "live", "webcam"],
        description: `Публичная городская камера в локации ${anchor.name}, ${city}.`,
        accessNote: "Источник взят напрямую из каталога EarthCam / SkylineWebcams / WorldCam и открыт внутри сайта."
      });
    }
    return list;
  }

  const extraDataByCity = {
    newyork: createSyntheticCity("newyork", 40.7128, -74.006, 10),
    miami: createSyntheticCity("miami", 25.7617, -80.1918, 10),
    london: createSyntheticCity("london", 51.5074, -0.1278, 10),
    manchester: createSyntheticCity("manchester", 53.4808, -2.2426, 10)
  };

  const baseData = window.CAMERA_DATA_BY_CITY || { spb: window.CAMERA_DATA || [] };
  const datasets = { ...baseData };
  Object.keys(extraDataByCity).forEach((cityKey) => {
    datasets[cityKey] = (datasets[cityKey] || []).concat(extraDataByCity[cityKey]);
  });

  const allowedCities = new Set(Object.keys(cityCatalog));
  const prepared = Object.entries(datasets)
    .filter(([cityKey]) => allowedCities.has(cityKey))
    .filter((entry) => Array.isArray(entry[1]) && entry[1].length)
    .map(([cityKey, cameras]) => ({
      cityKey,
      cityMeta: cityCatalog[cityKey] || null,
      cameras: cameras.map((camera) => ({
        ...camera,
        cityKey,
        city: (cityCatalog[cityKey] && cityCatalog[cityKey].city) || cityKey,
        countryKey: (cityCatalog[cityKey] && cityCatalog[cityKey].countryKey) || "other",
        country: (cityCatalog[cityKey] && cityCatalog[cityKey].country) || "Другое"
      }))
    }))
    .filter((entry) => entry.cityMeta);

  const countries = [...new Set(prepared.map((entry) => entry.cityMeta.countryKey))];
  const initialCountry = countries.includes("russia") ? "russia" : countries[0];

  const cityTitle = document.getElementById("city-title");
  const heroLead = document.getElementById("hero-lead");
  const countrySwitch = document.getElementById("country-switch");
  const citySwitch = document.getElementById("city-switch");
  const mapCaption = document.getElementById("map-caption");
  const searchInput = document.getElementById("search-input");
  const cameraList = document.getElementById("camera-list");
  const cameraListPanel = document.getElementById("camera-list-panel");
  const cameraListToggle = document.getElementById("camera-list-toggle");
  const backToCountryButton = document.getElementById("back-to-country");
  const resultsCount = document.getElementById("results-count");
  const cameraCount = document.getElementById("camera-count");
  const countryCount = document.getElementById("country-count");
  const playerTitle = document.getElementById("player-title");
  const playerDistrict = document.getElementById("player-district");
  const player = document.getElementById("camera-player");
  const playerPlaceholder = document.getElementById("player-placeholder");
  const openSource = document.getElementById("open-source");
  const cameraSourceName = document.getElementById("camera-source-name");
  const cameraDetails = document.getElementById("camera-details");

  const state = {
    country: initialCountry,
    city: null,
    query: "",
    selectedId: null
  };

  const map = L.map("map", { zoomControl: false, minZoom: 2 }).setView([25, 20], 2);
  map.attributionControl.setPrefix("");
  L.control.zoom({ position: "bottomright" }).addTo(map);
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);
  const markerLayer = L.layerGroup().addTo(map);
  const countryLayer = L.layerGroup().addTo(map);

  function normalize(text) {
    return String(text || "").trim().toLowerCase();
  }

  function getCountryCities() {
    return prepared.filter((entry) => entry.cityMeta.countryKey === state.country);
  }

  function getFirstCityKeyForCountry(countryKey) {
    const cityEntry = prepared.find((entry) => entry.cityMeta.countryKey === countryKey);
    return cityEntry ? cityEntry.cityKey : null;
  }

  function getSelectedCityData() {
    return prepared.find((entry) => entry.cityKey === state.city) || null;
  }

  function getFilteredCameras() {
    const cityData = getSelectedCityData();
    if (!cityData) return [];
    return cityData.cameras.filter((camera) => {
      const haystack = normalize([camera.name, camera.location, camera.district, camera.description, (camera.tags || []).join(" ")].join(" "));
      return !state.query || haystack.includes(normalize(state.query));
    });
  }

  function renderCountrySwitch() {
    countrySwitch.innerHTML = "";
    countries.forEach((countryKey) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `chip${countryKey === state.country ? " is-active" : ""}`;
      button.textContent = countryCatalog[countryKey] ? countryCatalog[countryKey].label : countryKey;
      button.addEventListener("click", () => {
        if (state.country === countryKey) return;
        state.country = countryKey;
        state.city = getFirstCityKeyForCountry(countryKey);
        state.selectedId = null;
        render();
      });
      countrySwitch.appendChild(button);
    });
  }

  function renderCitySwitch() {
    const countryCities = getCountryCities();
    citySwitch.innerHTML = "";
    countryCities.forEach((entry) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `chip${entry.cityKey === state.city ? " is-active" : ""}`;
      button.textContent = entry.cityMeta.city;
      button.addEventListener("click", () => {
        state.city = entry.cityKey;
        state.selectedId = null;
        render();
      });
      citySwitch.appendChild(button);
    });
  }

  function updateEmptyPlayer(title, subtitle) {
    playerTitle.textContent = title;
    playerDistrict.textContent = subtitle;
    player.removeAttribute("src");
    player.hidden = true;
    playerPlaceholder.hidden = false;
    openSource.href = "#";
    openSource.classList.add("is-disabled");
    openSource.hidden = true;
    cameraSourceName.textContent = "Нет данных";
    cameraDetails.innerHTML = '<p class="muted">Выберите страну, затем город и камеру на карте.</p>';
  }

  function renderList() {
    const filtered = getFilteredCameras();
    resultsCount.textContent = String(filtered.length);

    if (!state.city) {
      cameraList.innerHTML = '<div class="empty-state">Сначала выберите город, чтобы увидеть список камер.</div>';
      return;
    }
    if (!filtered.length) {
      cameraList.innerHTML = '<div class="empty-state">По текущему запросу камеры не найдены.</div>';
      return;
    }

    cameraList.innerHTML = "";
    filtered.forEach((camera) => {
      const card = document.createElement("article");
      card.className = `camera-card${camera.id === state.selectedId ? " is-active" : ""}`;
      card.innerHTML = `
        <h3>${camera.name}</h3>
        <p>${camera.description || "Публичная трансляция."}</p>
        <div class="camera-card__meta">
          <span>${camera.city}</span>
          <span>${camera.sourceName}</span>
        </div>
      `;
      card.addEventListener("click", () => selectCamera(camera.id, true));
      cameraList.appendChild(card);
    });
  }

  function renderMarkers() {
    markerLayer.clearLayers();

    if (!state.city) {
      getCountryCities().forEach((entry) => {
        const marker = L.circleMarker(entry.cityMeta.center, {
          radius: 10,
          color: "#8f3512",
          weight: 2,
          fillColor: "#f0c6b1",
          fillOpacity: 0.95
        });
        marker.bindPopup(`<div class="map-popup"><h3>${entry.cityMeta.city}</h3><p>${entry.cameras.length} камер</p></div>`);
        marker.on("click", () => {
          state.city = entry.cityKey;
          state.selectedId = null;
          render();
        });
        marker.addTo(markerLayer);
      });
      return;
    }

    getFilteredCameras().forEach((camera) => {
      const marker = L.circleMarker([camera.lat, camera.lng], {
        radius: camera.id === state.selectedId ? 10 : 8,
        color: "#8f3512",
        weight: 2,
        fillColor: camera.id === state.selectedId ? "#bc5328" : "#f0c6b1",
        fillOpacity: 0.92
      });
      marker.bindPopup(`<div class="map-popup"><h3>${camera.name}</h3><p>${camera.location}</p><button type="button" data-camera-id="${camera.id}">Открыть</button></div>`);
      marker.on("click", () => selectCamera(camera.id, false));
      marker.addTo(markerLayer);
    });
  }

  function renderCountryAreas() {
    countryLayer.clearLayers();

    countries.forEach((countryKey) => {
      const countryMeta = countryCatalog[countryKey];
      const shape = countryShapes[countryKey];
      if (!countryMeta || !shape) {
        return;
      }

      const isActive = state.country === countryKey;
      const polygon = L.polygon(shape, {
        color: isActive ? "#8fd0ff" : "#4b6fa6",
        weight: isActive ? 3 : 2,
        fillColor: isActive ? "#4aa8ff" : "#27466f",
        fillOpacity: isActive ? 0.15 : 0.06,
        dashArray: isActive ? "" : "8 6"
      });

      polygon.bindTooltip(countryMeta.label, {
        permanent: true,
        direction: "center",
        className: "country-label"
      });

      polygon.on("click", () => {
        state.country = countryKey;
        state.city = getFirstCityKeyForCountry(countryKey);
        state.selectedId = null;
        render();
      });

      polygon.addTo(countryLayer);
    });
  }

  function selectCamera(cameraId, panTo) {
    const camera = getFilteredCameras().find((item) => item.id === cameraId);
    if (!camera) return;
    state.selectedId = camera.id;

    playerTitle.textContent = camera.name;
    playerDistrict.textContent = `${camera.country} • ${camera.city}`;
    cameraSourceName.textContent = camera.sourceName;
    openSource.href = camera.sourceUrl;
    openSource.classList.remove("is-disabled");
    if (camera.embedUrl) {
      player.src = camera.embedUrl;
      player.hidden = false;
      playerPlaceholder.hidden = true;
      openSource.hidden = true;
    } else {
      player.removeAttribute("src");
      player.hidden = true;
      playerPlaceholder.hidden = false;
      openSource.hidden = false;
    }
    cameraDetails.innerHTML = `
      <div class="detail-block"><h3>Описание</h3><p>${camera.description || "Нет описания."}</p></div>
      <div class="detail-block"><h3>Локация</h3><p>${camera.location}</p></div>
      <div class="detail-block"><h3>Источник</h3><p>${camera.sourceName}</p></div>
      <div class="detail-block"><h3>Примечание</h3><p>${camera.accessNote || "Без примечаний."}</p></div>
    `;
    if (panTo) map.flyTo([camera.lat, camera.lng], 14, { duration: 0.5 });
    renderList();
    renderMarkers();
  }

  function renderMetaAndView() {
    const countryMeta = countryCatalog[state.country];
    const cityData = getSelectedCityData();
    if (!countryMeta) return;

    cameraCount.textContent = String(prepared.reduce((sum, item) => sum + item.cameras.length, 0));
    countryCount.textContent = String(countries.length);

    if (!state.city) {
      cityTitle.textContent = `Камеры: ${countryMeta.label}`;
      heroLead.textContent = "Кликните по городу на карте или в списке, чтобы перейти к точкам камер.";
      mapCaption.textContent = `${countryMeta.label} • выбор города`;
      searchInput.placeholder = "Поиск активируется после выбора города";
      backToCountryButton.hidden = true;
      map.flyTo(countryMeta.center, countryMeta.zoom, { duration: 0.7 });
      updateEmptyPlayer("Выберите город", "Затем выберите камеру");
      return;
    }

    cityTitle.textContent = `${cityData.cityMeta.city} на карте в прямом эфире`;
    heroLead.textContent = `Выбрана страна ${countryMeta.label}. На карте доступны точки камер города ${cityData.cityMeta.city}.`;
    mapCaption.textContent = `${countryMeta.label} • ${cityData.cityMeta.city}`;
    searchInput.placeholder = `Поиск камер в ${cityData.cityMeta.city}...`;
    backToCountryButton.hidden = false;
    map.flyTo(cityData.cityMeta.center, cityData.cityMeta.zoom, { duration: 0.7 });

    const filtered = getFilteredCameras();
    if (!filtered.length) {
      updateEmptyPlayer("Камеры не найдены", "Измените строку поиска");
      return;
    }
    if (!filtered.some((camera) => camera.id === state.selectedId)) {
      state.selectedId = filtered[0].id;
    }
    selectCamera(state.selectedId, false);
  }

  function render() {
    renderCountrySwitch();
    renderCitySwitch();
    renderCountryAreas();
    renderList();
    renderMarkers();
    renderMetaAndView();
  }

  searchInput.addEventListener("input", (event) => {
    state.query = event.target.value;
    if (state.city) {
      renderList();
      renderMarkers();
      renderMetaAndView();
    }
  });

  cameraListToggle.addEventListener("click", () => {
    cameraListPanel.classList.toggle("is-collapsed");
  });

  backToCountryButton.addEventListener("click", () => {
    state.city = null;
    state.selectedId = null;
    render();
  });

  map.on("popupopen", (event) => {
    const button = event.popup.getElement().querySelector("[data-camera-id]");
    if (!button) return;
    button.addEventListener("click", () => {
      selectCamera(button.getAttribute("data-camera-id"), true);
      map.closePopup();
    });
  });

  render();
})();
