const shell = document.querySelector(".shell");
const refresh = document.querySelector("#refresh");
const settings = document.querySelector("#settings");
const dialog = document.querySelector("#keyDialog");

const press = () => {
  shell.style.transform = "scale(.975)";
  setTimeout(() => shell.style.transform = "", 140);
};

refresh.addEventListener("click", () => {
  press();
  refresh.classList.remove("spinning");
  requestAnimationFrame(() => refresh.classList.add("spinning"));
});

refresh.addEventListener("animationend", () => refresh.classList.remove("spinning"));
settings.addEventListener("click", () => {
  press();
  dialog.showModal();
});
