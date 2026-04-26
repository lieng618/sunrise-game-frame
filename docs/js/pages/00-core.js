const pages = {};
const searchIndex = [];

function registerPage(key, title, desc, contentFn) {
    pages[key] = contentFn;
    searchIndex.push({ title: title, desc: desc, page: key });
}
