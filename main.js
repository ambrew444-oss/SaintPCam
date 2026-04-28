const topbar = document.querySelector(".topbar");
const menuButton = document.querySelector(".menu-button");
const systemItems = document.querySelectorAll(".system-item");

menuButton?.addEventListener("click", () => {
  const isOpen = topbar.classList.toggle("is-open");
  menuButton.setAttribute("aria-expanded", String(isOpen));
});

systemItems.forEach((item) => {
  item.addEventListener("click", () => {
    systemItems.forEach((entry) => entry.classList.remove("is-active"));
    item.classList.add("is-active");
  });
});
