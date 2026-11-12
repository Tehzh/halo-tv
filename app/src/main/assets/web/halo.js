/* HaloTV additions share the legacy upload/arrangement engine and TV preferences. No framework. */
(() => {
'use strict';
const labels = {
 dashboard:['管理首页','Overview'], apps:['应用管理','Apps'], desktop:['桌面外观','Appearance'], clock:['时钟天气','Clock & weather'], wallpapers:['壁纸屏保','Wallpaper & saver'], family:['儿童长辈','Family modes'], performance:['性能维护','Performance'], about:['关于','About'],
 intro:['把大屏，调成你喜欢的样子。','Make the big screen yours.'], local:['本地连接 · 设置直接同步到电视','Local connection · Changes sync to your TV'],
 introSub:['管理桌面、上传照片与视频，更换图标。手机与电视需在同一网络，无需注册账号。','Arrange apps, upload photos and videos, and personalize your TV. Stay on the same network; no account required.'],
 appHint:['整理顺序、替换图标与名称','Reorder apps, change icons and names'], layout:['桌面布局','Home layout'], single:['单排','Single row'], double:['双排','Two rows'], columns:['每排应用','Apps per row'], peek:['露出第二排','Peek at next row'], names:['显示全部应用名称','Show all app names'], plate:['Dock 底板','Dock background'], frosted:['磨砂玻璃','Frosted glass'], liquid:['液态玻璃','Liquid glass'], none:['不显示','None'], focus:['焦点样式','Focus style'], contrast:['高对比度','High contrast'], standard:['标准','Standard'], slide:['液态玻璃滑动','Liquid glass slide'], color:['桌面文字颜色','Desktop text color'], white:['白色','White'], gray:['深灰','Dark gray'], black:['黑色','Black'], iconPosition:['设置图标位置','Settings icon position'], left:['左边','Left'], right:['右边','Right'], leftCorner:['左上角','Top left','Вверху слева','Угорі ліворуч','Жоғарғы сол жақ'], corner:['右上角','Top right'], visibility:['设置图标显示','Settings icon visibility'], always:['常驻','Always visible'], fade:['淡出','Fade out'], hidden:['隐藏','Hidden'],
 clockPosition:['时钟栏位置','Clock bar position'], center:['居中','Center'], statusbar:['放入状态栏','Status bar'], clockSize:['时钟栏大小','Clock bar size'], normal:['默认','Default'], large:['大','Large'], bold:['加大加粗','Extra large & bold'], format:['桌面时间制','Home time format'], h24:['24 小时','24-hour'], h12:['12 小时','12-hour'], lunar:['农历日期','Lunar date'], capacity:['运存与存储信息','Memory & storage info'],
 saver:['屏保设置','Screensaver settings'], saverStart:['无操作后开始','Start after inactivity'], never:['从不','Never'], min:['分钟','minutes'], flipFormat:['翻页时钟时间制','Flip clock time format'], seconds:['翻页时钟显示秒钟','Show flip clock seconds'], backSaver:['返回键开始屏保','Back key starts screensaver'], mute:['动态壁纸静音','Mute video wallpaper'], fit:['视频完整显示（关闭则填满）','Fit video (off: fill screen)'], motion:['桌面视频持续播放','Keep home video playing'], saverClock:['视频 / 照片屏保显示时钟','Show clock over photo/video saver'], quality:['在线视频画质','Online video quality'], auto:['自动','Auto'],
 effects:['性能模式','Performance mode'], smooth:['流畅优先','Smoothness'], balanced:['均衡','Balanced'], visual:['效果优先','Visual quality'], sound:['按键声音','Navigation sounds'], tv:['电视','System'], halo:['Halo TV','Halo TV'], soft:['柔和','Soft'], crystal:['清脆','Crystal'], off:['关闭','Off'], idleClean:['空闲时自动清理后台','Clean background apps when idle'], diagnostics:['诊断与反馈','Diagnostics & feedback'],
 saved:['已同步到电视','Saved on TV'], failed:['未能保存，请检查连接或家长密码','Could not save. Check your connection or parental PIN.'], loading:['正在加载…','Loading…'], retry:['加载失败，点击重试','Could not load. Try again.'], connected:['电视已连接','TV connected'], offline:['连接中断，正在重试','Disconnected; retrying'],
 authorize:['验证家长密码','Verify parental PIN'], needPin:['请先在电视端设置家长密码，再通过此处验证。密码仅用于当前网页会话，不会保存到浏览器。','Set a parental PIN on TV first. It is held only in this page session, never in browser storage.'], pinPrompt:['请输入家长密码（方向密码使用 U / D / L / R）','Enter parental PIN (direction PIN: U / D / L / R)'],
 kids:['儿童模式','Kids mode'], elders:['长辈模式','Elders mode'], weekdays:['工作日每天（分钟，0 为不限）','Weekday daily limit (minutes; 0: unlimited)'], weekends:['周末每天（分钟，0 为不限）','Weekend daily limit (minutes; 0: unlimited)'], end:['最晚观看时间（空白为不限）','Latest viewing time (empty: unlimited)'], every:['每观看多久答题（分钟，0 为关闭）','Quiz after watching (minutes; 0: off)'], reward:['每次答题最多奖励（分钟）','Maximum quiz reward (minutes)'], reminder:['休息提示间隔（分钟，0 为关闭）','Rest reminder interval (minutes; 0: off)'], allowed:['仅显示勾选的应用','Show only selected apps'], familyNote:['两个模式的应用名单分别保存。长辈模式不限制时长、不答题；退出后不会因屏保或待机自动重新进入。退出需验证家长密码。','Each mode keeps its own app list. Elders mode has no limits or quizzes. Leaving a mode requires a PIN; sleep never re-enters it.'], saveRules:['保存此模式设置','Save mode settings'], enter:['进入此模式','Enter mode'], leave:['退出当前模式','Leave current mode'], active:['正在使用','Active'], inactive:['未启用','Inactive'],
 onlineImages:['必应壁纸图库','Bing wallpaper library'], onlineVideos:['在线视频图库','Online video library'], browse:['浏览图库','Browse library'], prev:['上一页','Previous'], next:['下一页','Next'], preview:['高清预览','HD preview'], apply:['设置为壁纸','Set wallpaper'], useVideo:['设为动态壁纸','Set video wallpaper'], all:['全部','All'], noItems:['暂无内容','No items'], rotation:['自动切换','Auto rotation'], daily:['每日自动切换','Daily'], onHome:['回到桌面切换','On return home'], custom:['自定义分钟数','Custom minutes'], confirm:['应用','Apply'],
 bingNotice:['图片来自 Bing，归档由 YanGN 提供；版权归各图片权利人所有。在线设置仅使用可替换缓存，不保存到本地照片。','Images from Bing; archive by YanGN. Rights belong to their respective owners. Cache mode keeps only the current working image.'], videoNotice:['视频来自 Apple；缩略图目录参考 Aerial Views。版权归各权利人所有；在线播放需要网络。','Videos from Apple; thumbnail catalog references Aerial Views. All rights remain with their owners. Streaming requires a connection.'],
 privacy:['该页面只在局域网内工作。请勿公开二维码或包含访问口令的地址。','This page works on your local network. Do not share its QR code or token URL publicly.'], attribution:['基于 Aerial 开源项目二次开发，由 Null_ 整理和发布。','Based on the open-source Aerial project; maintained and published by Null_.'], free:['Halo TV 完全免费下载与使用。其他渠道收取的费用并非作者收取；自愿打赏不影响任何功能使用。','Halo TV is free to download and use. Third-party charges are not collected by its author. Donations are optional.']
};
// Keep the page's five-language table convention; untranslated additions fall back to English.
Object.entries(labels).forEach(([key,pair])=>{const k='halo_'+key;KEYS.push(k);for(const l of LANGS){const value=l==='zh'?pair[0]:pair[1];TEXTS[l].push(value);I18N[l][k]=value;}});
const H=key=>t('halo_'+key);

const extraLabels={
 onlineVideo:['在线视频','Online videos'],apps:['应用与图标','Apps & icons'],family:['儿童与长辈','Kids & elders'],mobile:['手机管理中心','Phone management'],deviceConnected:['已连接','Connected'],deviceOffline:['未连接','Offline'],myDesktop:['我的桌面','My desktop'],adjust:['调整','Adjust'],uploadMedia:['上传照片或视频','Upload photos or videos'],manageTv:['管理电视','Manage TV'],personalize:['桌面与时钟','Desktop & clock'],personalizeHint:['布局、时钟与天气','Layout, clock & weather'],wallpapersHint:['静态、动态与屏保','Photos, videos & screensavers'],appsHint:['排序、名称与图标','Order, names & icons'],familyHint:['为家人设置专属桌面','A personal home for your family'],moreSettings:['更多设置','More settings'],moreHint:['性能、声音、诊断与关于','Performance, sounds, diagnostics & about'],donate:['打赏作者','Support the author'],donateHint:['自愿支持 · 微信打赏','Optional support · WeChat Pay'],syncHint:['所有更改直接同步到电视','Changes sync directly to your TV'],shortAttribution:['基于 Aerial 二次开发','Based on Aerial'],back:['返回','Back'],static:['静态壁纸','Photo wallpaper'],live:['动态壁纸','Video wallpaper'],localPhotos:['本地照片','Local photos'],localVideos:['本地视频','Local videos'],saverChoice:['屏保选择','Screensaver'],addPhotos:['上传照片','Upload photos'],addVideo:['上传视频','Upload videos'],mediaHint:['上传后可直接设为电视壁纸，原图不会拉伸。','Upload a photo or video to personalize your TV without stretching it.'],wallpaperPreview:['当前壁纸预览','Current wallpaper preview'],desktopPreview:['电视桌面画面','TV home still'],previewHint:['回到电视首页后，点击图片可刷新桌面画面。','Return to the TV home screen and tap to refresh its still.'],refreshPreview:['刷新桌面预览','Refresh desktop preview'],donateTitle:['谢谢你的支持','Thank you for your support'],donateText:['Halo TV 完全免费。喜欢的话，可以请作者喝杯咖啡。','Halo TV is free. If you enjoy it, you can buy the author a coffee.'],wechatPay:['微信打赏','WeChat Pay'],donateInstructions:['在微信内可长按图片尝试识别；也可以保存收款码，再到微信扫一扫中从相册选择。','In WeChat, try holding the image to recognize it. Or save it and select it from the album in WeChat Scan.'],saveQr:['保存微信收款码','Save WeChat payment code'],optional:['自愿支持，金额随心，不影响任何功能使用。','Support is optional and never affects access to any feature.'],gallerySource:['内容来源与版权','Sources & copyright'],verify:['验证','Verify'],up:['上','Up'],down:['下','Down'],directionLeft:['左','Left'],directionRight:['右','Right'],rulesHint:['使用说明','How it works'],weatherCity:['天气城市','Weather location'],returnHome:['返回首页','Back to overview'],languageLabel:['语言','Language'],uploadChoose:['选择上传方式','Choose what to upload'],photoHint:['图片会自动适配电视画面','Images adapt to your TV display'],videoHint:['上传后可用于动态桌面或屏保','Use videos as wallpapers or screensavers'],savedHint:['选择后自动保存到电视','Selections save automatically on TV']
};
for(const[key,pair]of Object.entries(extraLabels)){const k='halo_'+key;KEYS.push(k);for(const l of LANGS){const value=l==='zh'?pair[0]:pair[1];TEXTS[l].push(value);I18N[l][k]=value;}}
const updateLabels = {
 greetingShown: ['显示问候语', 'Show greeting', 'Приветствие', 'Привітання', 'Сәлемдесу'],
 timeShown: ['显示时间', 'Show time', 'Время', 'Час', 'Уақыт'],
 dateShown: ['显示日期', 'Show date', 'Дата', 'Дата', 'Күн'],
 weatherShown: ['显示天气', 'Show weather', 'Погода', 'Погода', 'Ауа райы'],
 updateApk: ['推送安装包更新','Send an update APK','Отправить APK','Надіслати APK','APK жіберу'],
 updateApkHelp: ['仅允许推送与电视当前版本类型一致的 Halo TV 新版安装包，其他应用的安装包会被拒绝。上传后请在电视上确认安装。','Only newer Halo TV APKs matching your TV edition are accepted. Other apps are rejected. Confirm installation on your TV after transfer.','Принимается только новый APK Halo TV той же редакции. Другие приложения отклоняются. Подтвердите установку на телевизоре.','Доступні лише нові APK Halo TV тієї самої редакції. Інші додатки відхиляються. Підтвердьте встановлення на телевізорі.','Тек Halo TV бағдарламасының дәл сол түрінің жаңа APK файлы қабылданады. Басқа қолданбалар қабылданбайды. Теледидарда растаңыз.'],
 updateApkSend: ['发送到电视','Send to TV','Отправить на телевизор','Надіслати на телевізор','Теледидарға жіберу'],
 updateApkSending: ['正在传输并校验安装包…','Sending and checking the APK…','Передача и проверка APK…','Передавання й перевірка APK…','APK жіберіліп, тексерілуде…'],
 updateApkReady: ['安装包已通过校验。请在电视的「控制中心 → 软件更新」确认安装。','APK verified. Confirm under Control Center → Software update on your TV.','APK проверен. Подтвердите на телевизоре: Пункт управления → Обновление ПО.','APK перевірено. Підтвердьте на телевізорі: Центр керування → Оновлення ПЗ.','APK тексерілді. Теледидарда Басқару орталығы → Бағдарламаны жаңарту бөлімінде растаңыз.'],
 updateApkBusy: ['电视正在接收或已有安装包待确认，请先处理当前更新。','The TV is receiving an APK or waiting for confirmation. Finish the current update first.','Телевизор уже принимает APK или ждёт подтверждения.','Телевізор уже приймає APK або очікує підтвердження.','Теледидар APK қабылдап жатыр немесе растауды күтуде.'],
 updateApkSize: ['请选择不超过 64 MB 的安装包。','Select an APK up to 64 MB.','Выберите APK размером до 64 МБ.','Виберіть APK розміром до 64 МБ.','64 МБ-тан аспайтын APK файлын таңдаңыз.'],
 updateApkSpace: ['电视可用存储空间不足。','The TV is low on storage.','На телевизоре недостаточно места.','На телевізорі недостатньо місця.','Теледидарда бос орын жеткіліксіз.'],
 updateApkWrong: ['这个文件不是可识别的 Halo TV 安装包。','This file is not a readable Halo TV APK.','Это не действительный APK Halo TV.','Це не дійсний APK Halo TV.','Бұл жарамды Halo TV APK файлы емес.'],
 updateApkSignature: ['安装包签名与 Halo TV 发布签名不一致。','The APK does not match the Halo TV release signature.','Подпись APK не совпадает с подписью релиза Halo TV.','Підпис APK не збігається з підписом випуску Halo TV.','APK қолтаңбасы Halo TV шығарылымының қолтаңбасына сәйкес емес.'],
 updateApkSignatureRead: ["电视系统未能读取安装包或当前应用的签名，已安全拒绝。请重新下载完整 APK；若仍失败，请反馈电视型号和 Android 版本。", "The TV could not read signing data. The upload was safely rejected. Download the complete APK again; if it persists, report the TV model and Android version.", "Телевизор не смог прочитать подпись. APK отклонён. Загрузите файл заново; сообщите модель и версию Android.", "Телевізор не зміг прочитати підпис. APK відхилено. Завантажте файл знову; повідомте модель і версію Android.", "Теледидар қолтаңбаны оқи алмады. APK қабылданбады. Қайта жүктеңіз; теледидар үлгісі мен Android нұсқасын хабарлаңыз."],
 updateApkInstalledSignature: ["安装包与电视已安装版本的签名不同，Android 无法覆盖更新。请先反馈电视当前版本和来源，不要直接卸载，以免丢失设置。", "The APK and installed app use different signers. Android cannot update it in place. Report the installed version and source before uninstalling, to avoid losing settings.", "Подпись APK отличается от установленной. Обновление поверх невозможно. Сообщите версию и источник; не удаляйте приложение.", "Підпис APK відрізняється від установленого. Оновлення поверх неможливе. Повідомте версію та джерело; не видаляйте додаток.", "APK пен орнатылған қолданба қолтаңбалары өзгеше. Үстінен жаңарту мүмкін емес. Нұсқа мен дереккөзді хабарлаңыз; қолданбаны жоймаңыз."],
 updateApkCurrent: ["电视当前版本", "Installed on TV", "Установлено на ТВ", "Установлено на ТВ", "Теледидардағы нұсқа"],
 updateApkVariant: ['请选择和电视当前安装类型相同的 Guard、Standard 或 Lite 安装包。','Choose the same edition installed on your TV: Guard, Standard or Lite.','Выберите ту же редакцию: Guard или Standard.','Виберіть те саме видання: Guard або Standard.','Орнатылған нұсқаны таңдаңыз: Guard немесе Standard.'],
 updateApkOlder: ['这个安装包的版本不比电视上已安装的版本新。','This APK is not newer than the installed version.','Версия APK не новее установленной.','Версія APK не новіша за встановлену.','APK нұсқасы орнатылған нұсқадан жаңа емес.'],
 updateApkUnsupported: ['这个安装包需要更高版本的 Android。','This APK requires a newer Android version.','Требуется более новая версия Android.','Потрібна новіша версія Android.','Android жүйесінің жаңа нұсқасы қажет.'],
 updateApkRestricted: ['请先在电视上退出儿童或长辈模式。','Exit Kids or Elders mode on the TV first.','Сначала выйдите из детского или упрощённого режима на телевизоре.','Спочатку вийдіть із дитячого або спрощеного режиму на телевізорі.','Алдымен теледидарда балалар немесе қарттар режимінен шығыңыз.'],
 updateApkFailed: ['传输失败，请检查连接后重试。','Transfer failed. Check the connection and try again.','Передача не удалась. Проверьте подключение.','Передавання не вдалося. Перевірте підключення.','Тасымал сәтсіз аяқталды. Байланысты тексеріңіз.']
};
const updateLanguages = ['zh','en','ru','uk','kk'];
for (const [key, translations] of Object.entries(updateLabels)) {
 const entry = 'halo_' + key;
 KEYS.push(entry);
 for (const language of LANGS) {
  const text = translations[updateLanguages.indexOf(language)] || translations[1];
  TEXTS[language].push(text);
  I18N[language][entry] = text;
 }
}
const iconFiles={dashboard:'television',home:'squares-four',desktop:'sliders-horizontal',clock:'clock',looks:'image',family:'users',performance:'lightning',more:'gear',about:'info',donate:'coffee',upload:'cloud-arrow-up',updateApk:'arrow-clockwise',back:'caret-left',next:'caret-right',refresh:'arrow-clockwise',personalize:'clock',static:'image',live:'play',saver:'television'};
function icon(name){const i=el('img','halo-icon');i.src=base+'icons/'+(iconFiles[name]||'gear')+'.svg';i.alt='';i.width=24;i.height=24;return i;}
const pageIds=['dashboard','home','personalize','desktop','clock','looks','static','live','saver','gallery','family','performance','more','about','donate','upload','updateApk'];
const page=id=>document.querySelector(`section[data-tab="${id}"]`);
for(const id of pageIds){if(!TABS.includes(id))TABS.push(id);if(!page(id)){const s=el('section');s.dataset.tab=id;s.hidden=true;$('main').append(s);}TITLES[id]='halo_'+({home:'apps',looks:'wallpapers',gallery:'browse',more:'moreSettings',upload:'uploadChoose'}[id]||id);}
$('nav').hidden=true;
const header=$('header');const title=$('#title');header.className='halo-header';header.replaceChildren();
const back=el('button','halo-back');back.type='button';back.append(icon('back'),el('span','',H('back')));back.onclick=()=>goBack();
const heading=el('div','halo-heading');heading.append(title);const subtitle=el('p','halo-subtitle',H('mobile'));heading.append(subtitle);
const status=el('span','halo-connected',H('deviceConnected'));status.setAttribute('role','status');header.append(back,heading,status);
const legacyBack=el('button');legacyBack.id='homeBack';legacyBack.hidden=true;legacyBack.onclick=()=>{arrView=null;renderArrange('back');};header.append(legacyBack);
const footer=el('footer','halo-footer');footer.append(el('div','',H('syncHint')),el('div','',H('shortAttribution')));$('main').append(footer);
const oldLooks=page('looks');const legacy=el('div');legacy.hidden=true;legacy.id='halo-legacy';$('main').append(legacy);
while(oldLooks.firstChild)legacy.append(oldLooks.firstChild);
const moreLegacy=page('more');while(moreLegacy.firstChild)legacy.append(moreLegacy.firstChild);
function card(label){const c=el('div','halo-card');if(label)c.append(el('h2','',H(label)));return c;}
function routeRow(id,label,hint,iconName){const b=el('button','halo-route');b.type='button';b.append(icon(iconName||id));const text=el('span','halo-route-text');text.append(el('b','',H(label)));if(hint)text.append(el('small','',H(hint)));b.append(text,icon('next'));b.lastChild.classList.add('halo-chevron');b.onclick=()=>show(id);return b;}
function group(...rows){const g=el('div','halo-group');g.append(...rows);return g;}
const dashboard=page('dashboard');const previewHeading=el('div','halo-section-heading');previewHeading.append(el('h2','',H('myDesktop')));const adjust=el('button','halo-adjust');adjust.append(el('span','',H('adjust')),icon('next'));adjust.onclick=()=>show('personalize');previewHeading.append(adjust);
const previewButton=el('button','halo-tv-preview');previewButton.type='button';previewButton.setAttribute('aria-label',H('refreshPreview'));const preview=el('img');preview.alt=H('myDesktop');preview.hidden=true;preview.decoding='async';previewButton.append(preview);previewButton.onclick=()=>refreshPreview(true);
const previewNote=el('p','halo-preview-note');previewNote.hidden=true;
const uploadButton=el('button','halo-upload-main');uploadButton.append(icon('upload'),el('span','',H('uploadMedia')));uploadButton.onclick=()=>show('upload');
const pushButton = el('button','halo-upload-main halo-upload-apk');
pushButton.type = 'button';
pushButton.append(icon('updateApk'), el('span','',H('updateApk')));
pushButton.onclick = () => show('updateApk');
const lite = window.haloLite === true;
if (lite) {
 const wording = {
  simpleClock: ['时钟', 'Clock', 'Часы', 'Годинник', 'Сағат'],
  live: ['本地视频屏保', 'Local video screensaver', 'Локальное видео', 'Локальне відео', 'Жергілікті бейне'],
  wallpapersHint: ['必应图片、本地照片与屏保', 'Bing, local photos and screensavers', 'Bing, фото и заставка', 'Bing, фото й заставка', 'Bing, фото және экран сақтағыш'],
  moreHint: ['声音、诊断与关于', 'Sound, diagnostics and about', 'Звук, диагностика и сведения', 'Звук, діагностика й відомості', 'Дыбыс, диагностика және ақпарат'],
  performance: ['声音与诊断', 'Sound and diagnostics', 'Звук и диагностика', 'Звук і діагностика', 'Дыбыс және диагностика'],
 };
 for (const [key, values] of Object.entries(wording)) LANGS.forEach((lang, i) => { I18N[lang]['halo_' + key] = values[updateLanguages.indexOf(lang)] || values[1]; });
}
const uploadActions = el('div','halo-home-uploads');
uploadActions.append(uploadButton);
uploadActions.append(pushButton);
dashboard.append(previewHeading,previewButton,previewNote,uploadActions,el('h2','halo-section-label',H('manageTv')),group(routeRow('personalize','personalize','personalizeHint'),routeRow('looks','wallpapers','wallpapersHint'),routeRow('home','apps','appsHint'),routeRow('family','family','familyHint')),group(routeRow('more','moreSettings','moreHint')),group(routeRow('donate','donate','donateHint')));
page('personalize').append(group(routeRow('desktop','desktop','plate'),routeRow('clock','clock','personalizeHint')),el('p','halo-note',H('savedHint')));
page('more').append(group(routeRow('performance','performance','moreHint'),routeRow('about','about','shortAttribution')));
const langRow=$('#lang').closest('.list');if(langRow){const oldModel=langRow.firstElementChild;oldModel.hidden=true;page('more').append(langRow);}page('more').append(el('p','halo-note',H('privacy')));
// Keep weather geocoding, diagnosis, arrangement, and upload handlers from the existing engine.
const weatherSwitch=$('#swWeather');weatherSwitch.closest('label').hidden=true;
const weatherBox=weatherSwitch.closest('.list');const weatherTitle=weatherBox.previousElementSibling;weatherTitle.textContent=H('weatherCity');weatherTitle.className='halo-section-label';page('clock').append(weatherTitle,weatherBox,$('#wxHint'));
const diagTitle=$('#diagBtn').closest('.list').previousElementSibling;diagTitle.textContent=H('diagnostics');diagTitle.className='halo-section-label';page('performance').append(diagTitle,$('#diagBtn').closest('.list'),$('#diag'),$('#diagActions'));
const about=card();about.append(el('p','',H('attribution')),el('p','',H('free')),el('h2','',H('gallerySource')),el('p','',H('bingNotice')));if(!lite)about.append(el('p','',H('videoNotice')));for(const url of ['https://github.com/Null9797/halo-tv','https://github.com/pengchujin/aerial-tv','https://github.com/ygnstudio/bing_wallpaper_archive',...(!lite?['https://github.com/theothernt/AerialViews']:[])]){const a=el('a','',url);a.href=url;a.target='_blank';a.rel='noopener';const p=el('p');p.append(a);about.append(p);}page('about').append(about);
const settingRoots={};for(const id of ['desktop','clock','saver','live','performance']){const root=card();root.id='halo-settings-'+id;settingRoots[id]=root;page(id).prepend(root);}
const opt=(v,k)=>[v,H(k)];
const fields={desktop:[['oneRowHome','layout',[opt(true,'single'),opt(false,'double')]],['homeColumns','columns',[[4,'4'],[5,'5'],[6,'6']]],['peekNextRow','peek'],['showAllNames','names'],['plateStyle','plate',[opt(0,'frosted'),opt(1,'liquid'),opt(2,'none')]],['focusStyle','focus',[opt('high_contrast','contrast'),opt('standard','standard'),opt('liquid_slide','slide')]],['clockTextColor','color',[opt(0,'white'),opt(1,'gray'),opt(2,'black')]],['settingsIconPosition','iconPosition',[opt(0,'leftCorner'),opt(1,'right'),opt(2,'corner')]],['settingsIconVisibility','visibility',[opt(0,'always'),opt(1,'fade'),opt(2,'hidden')]]],clock:[['clockPosition','clockPosition',[opt(0,'left'),opt(1,'right'),opt(2,'center'),opt(3,'statusbar')]],['clockSizeMode','clockSize',[opt(0,'normal'),opt(1,'large'),opt(2,'bold')]],['clock24Hour','format',[opt(true,'h24'),opt(false,'h12')]],['showHomeGreeting','greetingShown'],['showHomeTime','timeShown'],['showHomeDate','dateShown'],['showWeather','weatherShown'],['showLunarDate','lunar'],['showDeviceCapacity','capacity']],saver:[['screensaverMinutes','saverStart',[opt(0,'never'),...[2,5,10,20].map(n=>[n,n+' '+H('min')])]],['backStartsScreensaver','backSaver'],['flipClock24Hour','flipFormat',[opt(true,'h24'),opt(false,'h12')]],['flipClockSeconds','seconds'],['screensaverClock','saverClock'],['aerialQuality','quality',[opt('auto','auto'),['1080p','1080p'],['4k','4K'],['dolby_vision','4K Dolby Vision']]]],live:[['videoWallpaperMuted','mute'],['videoWallpaperFit','fit'],['wallpaperAlwaysMoves','motion']],performance:[['effects','effects',[opt('lite','smooth'),opt('auto','balanced'),opt('full','visual')]],['uiSounds','sound',[opt('tv','tv'),opt('aerial','halo'),opt('soft','soft'),opt('crystal','crystal'),opt('off','off')]],['autoCleanIdle','idleClean']]};
if (lite) {
  fields.desktop = fields.desktop.filter(([key]) => !key.startsWith('settingsIcon'));
  fields.desktop.find(([key]) => key === 'plateStyle')[2] = [opt(0,'frosted'), opt(2,'none')];
  fields.desktop.find(([key]) => key === 'focusStyle')[2] = [opt('high_contrast','contrast'), opt('standard','standard')];
  fields.clock.find(([key]) => key === 'clock24Hour').splice(0, 3, 'clockHours', 'format', [opt('system','auto'), opt('24','h24'), opt('12','h12')]);
  fields.saver = fields.saver.filter(([key]) => key !== 'aerialQuality');
  fields.performance = [['uiSounds','sound',[opt('tv','tv'),opt('aerial','halo'),opt('off','off')]]];
  fields.live = [];
}
let saving=false;
async function save(path,body){if(saving)return false;saving=true;try{let result=await api(path,body);if(result.kidsLocked&&await authorize())result=await api(path,body);if(result.saved===false||result.kidsLocked){take(result);toast(H('failed'),true);return false;}take(result);toast(H('saved'));return true;}catch(e){toast(H('failed'),true);return false;}finally{saving=false;}}
function settingRow(key,label,options){const row=el('label','halo-row');row.append(el('span','',H(label)));const input=el(options?'select':'input');input.dataset.haloKey=key;if(options){for(const [value,text]of options){const o=el('option','',text);o.value=String(value);input.append(o);}input.value=String(state.halo[key]);}else{input.type='checkbox';input.checked=state.halo[key]===true;}input.onchange=async()=>{input.disabled=true;const value=options?input.value:String(input.checked);const ok=await save('api/halo/settings',{key,value});if(!ok){if(options)input.value=String(state.halo[key]);else input.checked=!!state.halo[key];}input.disabled=false;};row.append(input);return row;}
let settingsStamp='';function renderSettings(){if(!state||!state.halo)return;apkBuild.textContent=H('updateApkCurrent')+': Halo TV '+(state.halo.version||'')+' · '+(state.halo.edition||(lite?'Lite':''));const stamp=JSON.stringify(state.halo)+state.screensaver;if(stamp===settingsStamp)return;settingsStamp=stamp;for(const [id,defs] of Object.entries(fields)){const root=settingRoots[id];root.querySelectorAll('.halo-row').forEach(n=>n.remove());for(const f of defs){if(id==='saver'&&((f[0].startsWith('flipClock')&&state.screensaver!=='flip_clock')||(f[0]==='aerialQuality'&&state.screensaver!=='aerial')))continue;root.append(settingRow(...f));}}status.textContent=H('deviceConnected');status.classList.remove('offline');}
// The original family form predates HaloTV quizzes and elders' independent app list.
renderKids=()=>{};
let familyKind='kids',familyStamp='',familyDirty=false;const familyRoot=card('family');familyRoot.oninput=()=>{familyDirty=true;};page('family').append(familyRoot);
let authResolve=null;
const engineCloseSheet=closeSheet,engineOpenSheet=openSheet;
function stopSheetMedia(){document.querySelectorAll('#sheetBody video').forEach(v=>{v.pause();v.removeAttribute('src');v.load();});}
openSheet=function(build){stopSheetMedia();engineOpenSheet(build);};
closeSheet=function(){stopSheetMedia();if(authResolve){authResolve(false);authResolve=null;}engineCloseSheet();};
async function authorize(){if(!state.kids.pinSet){toast(H('needPin'),true);return false;}return new Promise(resolve=>{authResolve=resolve;openSheet(body=>{body.append(el('h2','',H('authorize')));const form=el('form','halo-pin-form');const input=el('input');input.type='password';input.autocomplete='off';input.maxLength=4;input.setAttribute('aria-label',H('authorize'));input.inputMode=state.kids.pinArrows?'text':'numeric';input.readOnly=state.kids.pinArrows;form.append(input);if(state.kids.pinArrows){const arrows=el('div','halo-actions');for(const[code,label]of [['U','up'],['D','down'],['L','directionLeft'],['R','directionRight']]){const b=el('button','halo-action secondary',H(label));b.type='button';b.onclick=()=>{if(input.value.length<4)input.value+=code;};arrows.append(b);}form.append(arrows);}const verify=el('button','halo-action',H('verify'));verify.type='submit';form.append(verify);form.onsubmit=async e=>{e.preventDefault();if(input.value.length!==4)return;verify.disabled=true;try{await kidsUnlock(input.value);if(kidsPin){authResolve=null;engineCloseSheet();resolve(true);}else input.value='';}finally{verify.disabled=false;}};body.append(form);const cancel=el('button','halo-action secondary',t('cancel'));cancel.onclick=()=>closeSheet();body.append(cancel);setTimeout(()=>{if(!input.readOnly)input.focus();},200);});});}
function numberField(root,label,value,max=1440){const row=el('label','halo-row');row.append(el('span','',H(label)));const input=el('input');input.type='number';input.min='0';input.max=String(max);input.step='1';input.value=String(value);row.append(input);root.append(row);return input;}
function renderFamily(force){if(!state)return;const k=state.kids||{};const stamp=JSON.stringify(k)+familyKind+!!kidsPin;if(!force&&(stamp===familyStamp||familyDirty))return;if(familyRoot.contains(document.activeElement)&&!force)return;familyDirty=false;familyStamp=stamp;familyRoot.replaceChildren();const help=el('details','halo-help');help.append(el('summary','',H('rulesHint')),el('p','',H('familyNote')));familyRoot.append(help);
 const tabs=el('div','halo-actions');for(const kind of ['kids','elders']){const b=el('button','halo-action'+(familyKind===kind?'':' secondary'),H(kind));b.onclick=()=>{familyKind=kind;renderFamily(true);};tabs.append(b);}familyRoot.append(tabs);
 const info=el('p','',H(k.active?'active':'inactive')+(k.active?' · '+H(k.kind):''));familyRoot.append(info);const auth=el('button','halo-action secondary',H('authorize'));auth.onclick=authorize;familyRoot.append(auth);if(!k.pinSet)familyRoot.append(el('p','halo-note',H('needPin')));
 const form=el('form');const values={};if(familyKind==='kids'){values.weekday=numberField(form,'weekdays',k.weekdayMinutes||0);values.weekend=numberField(form,'weekends',k.weekendMinutes||0);const timeRow=el('label','halo-row');timeRow.append(el('span','',H('end')));values.end=el('input');values.end.type='time';values.end.value=k.endMinute?minClock(k.endMinute).padStart(5,'0'):'';timeRow.append(values.end);form.append(timeRow);values.breakEvery=numberField(form,'every',k.breakEvery||0);values.quizRewardMax=numberField(form,'reward',k.quizRewardMax||10,120);values.quizRewardMax.min='1';}else values.elderReminderMinutes=numberField(form,'reminder',k.elderReminderMinutes||0);
 form.append(el('h2','halo-online-title',H('allowed')));const checks=el('div','halo-checks');const selected=new Set((familyKind==='kids'?k.kidsAllowed:k.eldersAllowed)||[]);for(const item of k.candidates||[]){const l=el('label');const check=el('input');check.type='checkbox';check.value=item.key;check.checked=selected.has(item.key);l.append(check,el('span','',item.label));checks.append(l);}form.append(checks);
 async function config(enter){if(!kidsPin){await authorize();if(!kidsPin)return;}if(!form.reportValidity())return;const body={editOnly:enter?'0':'1',kind:familyKind,allowed:[...checks.querySelectorAll('input:checked')].map(n=>n.value).join('\n'),enabled:enter?'1':(k.enabled?'1':'0')};for(const [key,input]of Object.entries(values)){if(key==='end'){body.end=input.value?String(Number(input.value.slice(0,2))*60+Number(input.value.slice(3))):'0';}else body[key]=input.value;}if(await save('api/kids/config',body)){if(enter)await save('api/kids/start',{});renderFamily(true);}}
 const actions=el('div','halo-actions');const submit=el('button','halo-action',H('saveRules'));submit.type='submit';form.onsubmit=e=>{e.preventDefault();config(false);};actions.append(submit);const enter=el('button','halo-action secondary',H('enter'));enter.type='button';enter.onclick=()=>config(true);actions.append(enter);if(k.active){const leave=el('button','halo-action secondary',H('leave'));leave.type='button';leave.onclick=async()=>{if(!kidsPin)await authorize();if(kidsPin)await save('api/kids/leave',{});};actions.append(leave);}form.append(actions);familyRoot.append(form);
}

function rotation(root,type){const holder=el('div','halo-inline');holder.append(el('span','',H('rotation')));const select=el('select');const key=type==='bing'?'bingMinutes':'videoMinutes';for(const [v,k]of [[0,'never'],[1440,'daily'],[-1,'onHome'],['custom','custom']]){const o=el('option','',H(k));o.value=String(v);select.append(o);}const current=state.halo[key];select.value=[0,1440,-1].includes(current)?String(current):'custom';const n=el('input');n.type='number';n.min='1';n.max='10080';n.value=String(current>0?current:60);n.style.width='90px';n.hidden=select.value!=='custom';select.onchange=()=>n.hidden=select.value!=='custom';const b=el('button','halo-action',H('confirm'));b.onclick=async()=>{if(select.value==='custom'&&!n.reportValidity())return;b.disabled=true;await save('api/halo/settings',{key,value:select.value==='custom'?n.value:select.value});b.disabled=false;};holder.append(select,n,b);root.append(holder);}
async function loadGallery(){const generation=++galleryGeneration;galleryHost.replaceChildren(el('p','',H('loading')));try{let data;if(galleryType==='bing')data=await api('api/halo/bing?page='+galleryPage+'&category='+encodeURIComponent(category));else{if(!allVideos.length)allVideos=(await api('api/halo/videos')).items;const filtered=allVideos.filter(v=>!category||v.category===category);data={items:filtered.slice(galleryPage*24,galleryPage*24+24),total:filtered.length,categories:[...new Set(allVideos.map(v=>v.category))]};}if(generation!==galleryGeneration||tab!=='gallery')return;galleryHost.replaceChildren();galleryHost.append(el('p','halo-note',H(galleryType==='bing'?'bingNotice':'videoNotice')));if(!lite)rotation(galleryHost,galleryType);const filters=el('div','halo-actions');const select=el('select');const option=el('option','',H('all'));option.value='';select.append(option);for(const c of data.categories||[]){if(!c)continue;const o=el('option','',c);o.value=c;select.append(o);}select.value=category;select.onchange=()=>{category=select.value;galleryPage=0;loadGallery();};filters.append(select);galleryHost.append(filters);const grid=el('div','halo-gallery');for(const item of data.items){const b=el('button');const image=el('img');image.loading='lazy';image.decoding='async';image.src=item.thumbnail||item.preview;image.alt='';b.append(image,el('span','',item.title||item.label));b.onclick=()=>previewOnline(item,galleryType);grid.append(b);}galleryHost.append(grid);if(!data.items.length)galleryHost.append(el('p','',H('noItems')));const actions=el('div','halo-actions');for(const [delta,key]of [[-1,'prev'],[1,'next']]){const b=el('button','halo-action secondary',H(key));b.disabled=delta<0?galleryPage===0:(galleryPage+1)*24>=data.total;b.onclick=()=>{galleryPage+=delta;loadGallery();};actions.append(b);}galleryHost.append(actions);}catch(e){if(generation!==galleryGeneration)return;const retry=el('button','halo-action secondary',H('retry'));retry.onclick=loadGallery;galleryHost.replaceChildren(retry);}}
async function previewOnline(item,type){openSheet(body=>{body.append(el('h2','',item.title||item.label));const img=el('img','halo-preview');img.src=item.thumbnail||item.preview;img.alt=item.title||item.label;body.append(img,el('p','foot',item.credit||H('videoNotice')));const actions=el('div','halo-actions');const button=(label,fn)=>{const b=el('button','halo-action',H(label));b.onclick=async()=>{b.disabled=true;await fn();b.disabled=false;};actions.append(b);};if(type==='bing'){button('preview',async()=>{try{const original=await api('api/halo/bing/original?date='+item.date);if(img.isConnected)img.src=original.url;}catch(e){toast(H('retry'),true);}});button('apply',()=>save('api/halo/bing/apply',{date:item.date}));}else {button('preview',async()=>{const existing=body.querySelector('video.halo-preview');if(existing){await existing.play().catch(()=>{});return;}const video=el('video','halo-preview');video.controls=true;video.preload='none';video.playsInline=true;video.muted=true;video.src=item.url;img.replaceWith(video);await video.play().catch(()=>{});});button('useVideo',()=>save('api/halo/video',{id:item.id}));}body.append(actions);});}

// Focused media subpages: no long mixed list and no sideways navigation.
page('looks').append(group(routeRow('static','static','localPhotos'),routeRow('live','live','localVideos'),routeRow('saver','saverChoice','saverStart')));
const galleryHost=el('div');page('gallery').append(galleryHost);let galleryGeneration=0,galleryType='',galleryPage=0,category='',allVideos=[];
function onlineRow(type){const row=routeRow('gallery',type==='bing'?'onlineImages':'onlineVideos','browse',type==='bing'?'looks':'live');row.onclick=()=>{galleryType=type;galleryPage=0;category='';show('gallery');loadGallery();};return row;}
page('static').prepend(group(onlineRow('bing')));if(!lite)page('live').prepend(group(onlineRow('video')));
const localPhotoRoot=card('localPhotos');const localPhotoGrid=el('div','grid');localPhotoRoot.append(localPhotoGrid);page('static').append(localPhotoRoot);
const photoInput=el('input');photoInput.type='file';photoInput.accept='image/*';photoInput.multiple=true;photoInput.hidden=true;photoInput.onchange=e=>{sendPhotos([...e.target.files]);photoInput.value='';};$('main').append(photoInput);
function photoUpload(){const b=el('button','halo-action');b.append(icon('upload'),el('span','',H('addPhotos')));b.onclick=()=>photoInput.click();return b;}
page('static').insertBefore(photoUpload(),localPhotoRoot);
const saverTitle=$('#ss').previousElementSibling;saverTitle.hidden=true;page('saver').prepend($('#ss'));page('saver').append($('#ssFoot'));$('#ssTimeBox').hidden=true;
if(!lite){const bingButton=$('#ss [data-mode="photos"]').cloneNode(true);bingButton.dataset.mode='bing_online';bingButton.querySelector('.txt').replaceChildren(el('span','',H('onlineImages')));bingButton.onclick=async()=>{await save('api/screensaver',{mode:'bing_online'});};$('#ss').append(bingButton);for(const[mode,label]of [['aerial','onlineVideo'],['photos','localPhotos'],['videos','localVideos']]){const b=$('#ss [data-mode="'+mode+'"]');b.querySelector('.txt>span').textContent=H(label);}}else {
 $('#ss [data-mode="aerial"]').remove();
 const clockButton = $('#ss [data-mode="flip_clock"]').cloneNode(true);
 clockButton.dataset.mode = 'clock';
 clockButton.querySelector('.txt>span').textContent = (lite ? H('simpleClock') : t('ss_clock'));
 $('#ss').append(clockButton);
}document.querySelectorAll('#ss [data-mode]').forEach(b=>b.onclick=()=>save('api/screensaver',{mode:b.dataset.mode}));
page('live').append($('#videos'));
const uploadGroup=group();const choosePhotos=routeRow('static','addPhotos','photoHint','static');choosePhotos.onclick=()=>photoInput.click();const chooseVideo=routeRow('live','addVideo','videoHint','live');chooseVideo.onclick=()=>$('#videoFile').click();uploadGroup.append(choosePhotos,chooseVideo);page('upload').append(uploadGroup,el('p','halo-note',H('mediaHint')));
const apkCard = card();
apkCard.classList.add('halo-apk-card');
const apkForm = el('form');
const apkInput = el('input');
apkInput.type = 'file';
apkInput.accept = '.apk,application/vnd.android.package-archive';
apkInput.required = true;
apkInput.setAttribute('aria-label', H('updateApk'));
const apkSend = el('button','halo-action',H('updateApkSend'));
apkSend.type = 'submit';
const apkStatus = el('p','halo-note');
apkStatus.setAttribute('role','status');
apkForm.append(apkInput, apkSend);
const apkBuild = el('p','halo-note');
apkCard.append(apkBuild, el('p','',H('updateApkHelp')), apkForm, apkStatus);
page('updateApk').append(apkCard);
const apkResults = {
 ready:'updateApkReady', busy:'updateApkBusy', invalid_size:'updateApkSize',
 no_space:'updateApkSpace', wrong_app:'updateApkWrong', wrong_signature:'updateApkSignature', signature_unavailable:'updateApkSignatureRead', installed_signature_mismatch:'updateApkInstalledSignature',
 wrong_variant:'updateApkVariant', not_newer:'updateApkOlder', unsupported:'updateApkUnsupported',
 restricted:'updateApkRestricted'
};
apkForm.onsubmit = async event => {
 event.preventDefault();
 const file = apkInput.files[0];
 if (!file) return;
 if (file.size < 1 || file.size > 64 * 1024 * 1024) {
  apkStatus.textContent = H('updateApkSize');
  return;
 }
 apkInput.disabled = apkSend.disabled = true;
 apkStatus.textContent = H('updateApkSending');
 try {
  const response = await fetch(base + 'api/halo/update-apk', {
   method:'POST',
   headers:{'Content-Type':'application/vnd.android.package-archive', 'X-Halo-Update':'1'},
   body:file
  });
  const {result} = await response.json();
  apkStatus.textContent = H(apkResults[result] || 'updateApkFailed');
  if (result === 'ready') apkForm.reset();
 } catch (_) {
  apkStatus.textContent = H('updateApkFailed');
 } finally {
  apkInput.disabled = apkSend.disabled = false;
 }
};
const donation=card();donation.classList.add('halo-donation');donation.append(icon('donate'),el('h2','',H('donateTitle')),el('p','',H('donateText')),el('h3','',H('wechatPay')));const qr=el('img','halo-payment-qr');qr.src=base+'api/halo/donate-wechat.png';qr.alt=H('wechatPay');qr.loading='lazy';donation.append(qr,el('p','',H('donateInstructions')));const saveQr=el('a','halo-action',H('saveQr'));saveQr.href=qr.src;saveQr.download='HaloTV-WeChat.png';donation.append(saveQr,el('p','halo-note',H('optional')));page('donate').append(donation);
let previewAt=0,previewUrl='',previewPending=false;
async function refreshPreview(force=false){if(previewPending||document.hidden||tab!=='dashboard'||(!force&&Date.now()-previewAt<30000))return;previewPending=true;previewButton.setAttribute('aria-busy','true');try{const response=await fetch(base+'api/halo/home-preview.jpg',{cache:'no-store'});if(!response.ok)throw Error('preview');const blob=await response.blob();const next=URL.createObjectURL(blob);if(previewUrl)URL.revokeObjectURL(previewUrl);previewUrl=next;preview.src=next;preview.hidden=false;previewAt=Date.now();previewNote.hidden=response.headers.get('X-Halo-Preview')==='desktop';previewNote.textContent=H('previewHint');}catch(e){previewNote.hidden=false;previewNote.textContent=H('retry');}finally{previewPending=false;previewButton.removeAttribute('aria-busy');}}
function renderPhotos(){localPhotoGrid.replaceChildren();const photos=(state.wallpapers||[]).filter(w=>w.kind==='photo'||w.kind==='auto'||w.id==='auto');for(const wp of photos)localPhotoGrid.append(window.card(wp));if(!photos.length)localPhotoGrid.append(el('p','empty',t('empty_photos')));}
const parents={home:'dashboard',personalize:'dashboard',desktop:'personalize',clock:'personalize',looks:'dashboard',static:'looks',live:'looks',saver:'looks',family:'dashboard',more:'dashboard',performance:'more',about:'more',donate:'dashboard',upload:'dashboard',updateApk:'dashboard'};
function goBack(){if(!sheet.hidden){closeSheet();return;}if(tab==='home'&&arrView!==null){arrView=null;renderArrange('back');return;}show(tab==='gallery'?(galleryType==='bing'?'static':'live'):(parents[tab]||'dashboard'));}
show=function(next,keepScroll=false,fromHistory=false){if(!pageIds.includes(next))next='dashboard';if(tab&&tab!==next)scrolls[tab]=window.scrollY;if(tab==='gallery'&&next!=='gallery'){galleryGeneration++;galleryHost.replaceChildren();}tab=next;document.querySelectorAll('section[data-tab]').forEach(s=>s.hidden=s.dataset.tab!==next);const isHome=next==='dashboard';back.hidden=isHome;subtitle.hidden=!isHome;status.hidden=!isHome;header.classList.toggle('home',isHome);title.textContent=isHome?'HaloTV':H(({home:'apps',looks:'wallpapers',more:'moreSettings',gallery:galleryType==='bing'?'onlineImages':'onlineVideos',upload:'uploadChoose'}[next]||next));legacyBack.hidden=true;footer.hidden=!isHome;if(!fromHistory){const url='#'+next;if(keepScroll)history.replaceState({halo:next},'',url);else if(location.hash!==url)history.pushState({halo:next},'',url);}window.scrollTo(0,keepScroll?(scrolls[next]||0):0);stateJson='';render();if(next==='dashboard')refreshPreview();};
window.addEventListener('popstate',()=>{if(!sheet.hidden)closeSheet();show(location.hash.slice(1)||'dashboard',true,true);});
render=function(){if(!state)return;const json=JSON.stringify(state);if(json===stateJson)return;stateJson=json;if(tab==='home'){renderInputs();renderArrange(false);legacyBack.hidden=true;}if(tab==='static')renderPhotos();if(tab==='live'||tab==='saver')renderScreensaver();if(tab==='clock')renderMore();renderSettings();if(tab==='family')renderFamily(false);status.textContent=H('deviceConnected');status.classList.remove('offline');};
let refreshing=false;
refresh=async function(){const active=document.activeElement;if(refreshing||document.hidden||(active&&/^(INPUT|SELECT|TEXTAREA)$/.test(active.tagName)))return;refreshing=true;try{state=await api('api/state');render();status.textContent=H('deviceConnected');status.classList.remove('offline');}catch(e){status.textContent=H('deviceOffline');status.classList.add('offline');}finally{refreshing=false;}};
show(pageIds.includes(haloInitialTab)?haloInitialTab:'dashboard',true);render();
})();
