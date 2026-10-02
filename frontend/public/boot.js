// Runs before first paint: lets the static boot placeholder in index.html match the home
// hero's night background, so the page does not flash white while the app downloads.
if (location.pathname === '/') document.documentElement.classList.add('boot-home');
