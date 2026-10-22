"use strict";
// Lista conservadora. Não é substituta para filtros completos (EasyList, uBlock Origin).
const blocked = new Set([
 "doubleclick.net","googlesyndication.com","googleadservices.com",
 "adservice.google.com","pagead2.googlesyndication.com",
 "google-analytics.com","googletagmanager.com","scorecardresearch.com",
 "adnxs.com","criteo.com","taboola.com","outbrain.com",
 "adsrvr.org","pubmatic.com","rubiconproject.com","casalemedia.com",
 "openx.net","moatads.com","smartadserver.com","quantserve.com",
 "hotjar.com","zedo.com","media.net","amazon-adsystem.com"
]);
function isBlocked(hostname) {
 const host = (hostname||"").toLowerCase().replace(/\.$/,"");
 for (const domain of blocked) {
   if (host===domain || host.endsWith("."+domain)) return true;
 }
 return false;
}
browser.webRequest.onBeforeRequest.addListener(
 details => {
   try { return {cancel:isBlocked(new URL(details.url).hostname)}; }
   catch(e){ return {cancel:false}; }
 },
 {urls:["<all_urls>"]},
 ["blocking"]
);
