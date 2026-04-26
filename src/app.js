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
    russia: { label: "Россия", center: [61, 96], zoom: 4 },
    usa: { label: "США", center: [39.8, -98.5], zoom: 4 },
    uk: { label: "Великобритания", center: [54.5, -2.2], zoom: 6 }
  };

  function createSyntheticCity(cityKey, baseLat, baseLng, sourceName, sourceUrl, count) {
    const list = [];
    for (let i = 1; i <= count; i += 1) {
      list.push({
        id: `${cityKey}-${i}`,
        name: `Камера ${i} • ${cityCatalog[cityKey].city}`,
        district: cityCatalog[cityKey].city,
        location: `${cityCatalog[cityKey].city}, зона ${i}`,
        lat: baseLat + (i % 5) * 0.01 - 0.02,
        lng: baseLng + (i % 4) * 0.012 - 0.018,
        sourceName,
        sourceUrl,
        embedUrl: "",
        tags: [cityKey, "live", sourceName.toLowerCase()],
        description: `Публичная точка наблюдения №${i} в городе ${cityCatalog[cityKey].city}.`,
        accessNote: "Для части международных источников доступен переход на оригинальную страницу трансляции."
      });
    }
    return list;
  }

  const extraDataByCity = {
    newyork: createSyntheticCity("newyork", 40.7128, -74.006, "EarthCam", "https://www.earthcam.com/", 10),
    miami: createSyntheticCity("miami", 25.7617, -80.1918, "EarthCam", "https://www.earthcam.com/", 10),
    london: createSyntheticCity("london", 51.5074, -0.1278, "SkylineWebcams", "https://www.skylinewebcams.com/en/webcam.html", 10),
    manchester: createSyntheticCity("manchester", 53.4808, -2.2426, "WorldCam", "https://worldcam.eu", 10)
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

  function normalize(text) {
    return String(text || "").trim().toLowerCase();
  }

  function getCountryCities() {
    return prepared.filter((entry) => entry.cityMeta.countryKey === state.country);
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
        state.city = null;
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
    } else {
      player.removeAttribute("src");
      player.hidden = true;
      playerPlaceholder.hidden = false;
    }
    cameraDetails.innerHTML = `
      <div class="detail-block"><h3>Описание</h3><p>${camera.description || "Нет описания."}</p></div>
      <div class="detail-block"><h3>Локация</h3><p>${camera.location}</p></div>
      <div class="detail-block"><h3>Источник</h3><p><a href="${camera.sourceUrl}" target="_blank" rel="noreferrer">${camera.sourceName}</a></p></div>
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
      map.flyTo(countryMeta.center, countryMeta.zoom, { duration: 0.7 });
      updateEmptyPlayer("Выберите город", "Затем выберите камеру");
      return;
    }

    cityTitle.textContent = `${cityData.cityMeta.city} на карте в прямом эфире`;
    heroLead.textContent = `Выбрана страна ${countryMeta.label}. На карте доступны точки камер города ${cityData.cityMeta.city}.`;
    mapCaption.textContent = `${countryMeta.label} • ${cityData.cityMeta.city}`;
    searchInput.placeholder = `Поиск камер в ${cityData.cityMeta.city}...`;
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
