(function () {
  const cityConfig = {
    spb: {
      label: "Санкт-Петербург",
      title: "Санкт-Петербург на карте в прямом эфире",
      lead: "Выбирайте районы и точки на интерактивной карте, чтобы быстро открыть доступные трансляции камер.",
      center: [59.93863, 30.31413],
      zoom: 11,
      searchPlaceholder: "Невский проспект, Дворцовая площадь, Лахта..."
    },
    moscow: {
      label: "Москва",
      title: "Москва на карте в прямом эфире",
      lead: "Переключайтесь по районам Москвы и открывайте публичные онлайн-камеры в несколько кликов.",
      center: [55.75586, 37.6177],
      zoom: 11,
      searchPlaceholder: "Тверская, ВДНХ, Садовое кольцо..."
    }
  };

  const datasets = window.CAMERA_DATA_BY_CITY || { spb: window.CAMERA_DATA || [] };
  const availableCities = Object.keys(cityConfig).filter((city) => Array.isArray(datasets[city]));
  const initialCity = availableCities.includes("spb") ? "spb" : availableCities[0];

  const cityTitle = document.getElementById("city-title");
  const heroLead = document.getElementById("hero-lead");
  const citySwitch = document.getElementById("city-switch");
  const mapCaption = document.getElementById("map-caption");
  const searchInput = document.getElementById("search-input");
  const districtFilters = document.getElementById("district-filters");
  const cameraList = document.getElementById("camera-list");
  const resultsCount = document.getElementById("results-count");
  const cameraCount = document.getElementById("camera-count");
  const playerTitle = document.getElementById("player-title");
  const playerDistrict = document.getElementById("player-district");
  const player = document.getElementById("camera-player");
  const playerPlaceholder = document.getElementById("player-placeholder");
  const openSource = document.getElementById("open-source");
  const cameraSourceName = document.getElementById("camera-source-name");
  const cameraDetails = document.getElementById("camera-details");

  const state = {
    city: initialCity,
    query: "",
    district: "Все",
    selectedId: null
  };

  function getCameras() {
    return Array.isArray(datasets[state.city]) ? datasets[state.city] : [];
  }

  const map = L.map("map", {
    zoomControl: false,
    minZoom: 9
  }).setView(cityConfig[state.city].center, cityConfig[state.city].zoom);

  map.attributionControl.setPrefix("");

  L.control
    .zoom({
      position: "bottomright"
    })
    .addTo(map);

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);

  const markerLayer = L.layerGroup().addTo(map);

  function formatTags(tags) {
    return tags
      .map((tag) => `<span class="chip">${tag}</span>`)
      .join("");
  }

  function normalize(text) {
    return String(text || "")
      .trim()
      .toLowerCase();
  }

  function getDistricts() {
    const cameras = getCameras();
    return ["Все"].concat(
      [...new Set(cameras.map((camera) => camera.district))].sort((a, b) =>
        a.localeCompare(b, "ru")
      )
    );
  }

  function getFilteredCameras() {
    const cameras = getCameras();
    return cameras.filter((camera) => {
      const matchesDistrict = state.district === "Все" || camera.district === state.district;
      const haystack = normalize(
        [camera.name, camera.location, camera.district, camera.tags.join(" "), camera.description].join(" ")
      );
      const matchesQuery = !state.query || haystack.includes(normalize(state.query));
      return matchesDistrict && matchesQuery;
    });
  }

  function updateSelectedCamera(camera, options) {
    playerTitle.textContent = camera.name;
    playerDistrict.textContent = `${camera.district} • ${camera.location}`;
    cameraSourceName.textContent = camera.sourceName;
    openSource.href = camera.sourceUrl;
    openSource.classList.remove("is-disabled");

    if (camera.embedUrl) {
      player.src = camera.embedUrl;
      player.hidden = false;
      player.style.display = "block";
      playerPlaceholder.hidden = true;
      playerPlaceholder.style.display = "none";
    } else {
      player.removeAttribute("src");
      player.hidden = true;
      player.style.display = "none";
      playerPlaceholder.hidden = false;
      playerPlaceholder.style.display = "grid";
    }

    cameraDetails.innerHTML = `
      <div class="detail-block">
        <h3>Описание</h3>
        <p>${camera.description || "Нет описания."}</p>
      </div>
      <div class="detail-block">
        <h3>Локация</h3>
        <p>${camera.location}</p>
      </div>
      <div class="detail-block">
        <h3>Оригинальная страница</h3>
        <p><a href="${camera.sourceUrl}" target="_blank" rel="noreferrer">${camera.sourceName}</a></p>
      </div>
      <div class="detail-block">
        <h3>Теги</h3>
        <div class="tags">${formatTags(camera.tags)}</div>
      </div>
      <div class="detail-block">
        <h3>Примечание</h3>
        <p>${camera.accessNote}</p>
      </div>
    `;

    if (!options || options.panTo !== false) {
      map.flyTo([camera.lat, camera.lng], 14, {
        duration: 0.6
      });
    }
  }

  function selectCamera(cameraId, options) {
    const camera = getCameras().find((item) => item.id === cameraId);
    if (!camera) {
      return;
    }

    state.selectedId = camera.id;
    updateSelectedCamera(camera, options);

    if (!options || options.syncCollections !== false) {
      renderList();
      renderMarkers();
    }
  }

  function renderCitySwitch() {
    citySwitch.innerHTML = "";

    availableCities.forEach((cityKey) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `chip${cityKey === state.city ? " is-active" : ""}`;
      button.textContent = cityConfig[cityKey].label;
      button.addEventListener("click", () => {
        if (cityKey === state.city) {
          return;
        }
        state.city = cityKey;
        state.query = "";
        state.district = "Все";
        state.selectedId = null;
        searchInput.value = "";
        render();
      });
      citySwitch.appendChild(button);
    });
  }

  function renderFilters() {
    districtFilters.innerHTML = "";

    getDistricts().forEach((district) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `chip${district === state.district ? " is-active" : ""}`;
      button.textContent = district;
      button.addEventListener("click", () => {
        state.district = district;
        render();
      });
      districtFilters.appendChild(button);
    });
  }

  function renderList() {
    const filtered = getFilteredCameras();
    resultsCount.textContent = `${filtered.length} шт.`;
    cameraCount.textContent = String(getCameras().length);

    if (!filtered.length) {
      cameraList.innerHTML =
        '<div class="empty-state">По текущему фильтру ничего не найдено. Попробуйте снять фильтр или изменить поисковый запрос.</div>';
      return;
    }

    cameraList.innerHTML = "";

    filtered.forEach((camera) => {
      const card = document.createElement("article");
      card.className = `camera-card${camera.id === state.selectedId ? " is-active" : ""}`;
      card.innerHTML = `
        <h3>${camera.name}</h3>
        <p>${camera.description || "Публичная городская трансляция."}</p>
        <div class="camera-card__meta">
          <span>${camera.district}</span>
          <span>Прямой эфир</span>
        </div>
      `;
      card.addEventListener("click", () => selectCamera(camera.id));
      cameraList.appendChild(card);
    });
  }

  function renderMarkers() {
    const filtered = getFilteredCameras();
    markerLayer.clearLayers();

    filtered.forEach((camera) => {
      const marker = L.circleMarker([camera.lat, camera.lng], {
        radius: camera.id === state.selectedId ? 10 : 8,
        color: "#8f3512",
        weight: 2,
        fillColor: camera.id === state.selectedId ? "#bc5328" : "#f0c6b1",
        fillOpacity: 0.92
      });

      marker.bindPopup(`
        <div class="map-popup">
          <h3>${camera.name}</h3>
          <p>${camera.location}</p>
          <button type="button" data-camera-id="${camera.id}">Открыть трансляцию</button>
        </div>
      `);

      marker.on("click", () => {
        selectCamera(camera.id, { panTo: false });
      });

      marker.addTo(markerLayer);
    });
  }

  function ensureSelectedCamera() {
    const filtered = getFilteredCameras();
    if (!filtered.length) {
      state.selectedId = null;
      playerTitle.textContent = "Камеры не найдены";
      playerDistrict.textContent = "Измените фильтр";
      player.removeAttribute("src");
      player.hidden = true;
      player.style.display = "none";
      playerPlaceholder.hidden = false;
      playerPlaceholder.style.display = "grid";
      openSource.href = "#";
      openSource.classList.add("is-disabled");
      cameraSourceName.textContent = "Нет данных";
      cameraDetails.innerHTML =
        '<p class="muted">Когда вы выберете доступную камеру, здесь появятся ее описание, источник и служебные заметки.</p>';
      return;
    }

    if (!filtered.some((camera) => camera.id === state.selectedId)) {
      state.selectedId = filtered[0].id;
    }

    selectCamera(state.selectedId, { panTo: false, syncCollections: false });
  }

  function renderCityMeta() {
    const config = cityConfig[state.city];
    cityTitle.textContent = config.title;
    heroLead.textContent = config.lead;
    mapCaption.textContent = config.label;
    searchInput.placeholder = config.searchPlaceholder;
    map.flyTo(config.center, config.zoom, { duration: 0.5 });
  }

  function render() {
    renderCityMeta();
    renderCitySwitch();
    renderFilters();
    renderList();
    renderMarkers();
    ensureSelectedCamera();
  }

  searchInput.addEventListener("input", (event) => {
    state.query = event.target.value;
    render();
  });

  map.on("popupopen", (event) => {
    const button = event.popup.getElement().querySelector("[data-camera-id]");
    if (!button) {
      return;
    }

    button.addEventListener("click", () => {
      const cameraId = button.getAttribute("data-camera-id");
      selectCamera(cameraId);
      map.closePopup();
    });
  });

  render();
})();
