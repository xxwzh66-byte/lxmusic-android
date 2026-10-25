(() => {
  // shim/request.js
  var httpFetch = (url, options = { method: "get" }) => window.httpFetch(url, options);

  // shim/index.js
  var decodeName = (str) => {
    if (!str) return "";
    return String(str).replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#39;/g, "'").replace(/&nbsp;/g, " ");
  };
  var formatPlayTime = (seconds) => {
    if (!seconds || seconds < 0) return "00:00";
    const s = Math.round(seconds);
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return String(m).padStart(2, "0") + ":" + String(sec).padStart(2, "0");
  };
  var sizeFormate = (size) => {
    if (!size || size <= 0) return "0B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let v = Number(size), i = 0;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i++;
    }
    return i === 0 ? size + "B" : v.toFixed(1) + units[i];
  };

  // shim/node-builtins.js
  var createCipheriv = (...a) => window.nodeCrypto.createCipheriv(...a);
  var createDecipheriv = (...a) => window.nodeCrypto.createDecipheriv(...a);
  var createHash = (...a) => window.nodeCrypto.createHash(...a);
  var randomBytes = (...a) => window.nodeCrypto.randomBytes(...a);
  var publicEncrypt = (...a) => window.nodeCrypto.publicEncrypt(...a);
  var constants = { RSA_NO_PADDING: 3 };
  var inflate = (...a) => window.nodeZlib.inflate(...a);
  var deflate = (...a) => window.nodeZlib.deflate(...a);
  var node_builtins_default = {
    createCipheriv,
    createDecipheriv,
    createHash,
    randomBytes,
    publicEncrypt,
    constants,
    inflate,
    deflate
  };

  // shim/musicSdk-utils.js
  var toMD5 = (str) => window.lxNative.md5(String(str));
  var formatSingerName = (singers, nameKey = "name", join = "\u3001") => {
    if (Array.isArray(singers)) {
      const names = [];
      singers.forEach((item) => {
        const n = item && item[nameKey];
        if (n) names.push(n);
      });
      return names.join(join);
    }
    return String(singers == null ? "" : singers);
  };

  // lx/kw/util.js
  var buf_key = Buffer.from("yeelion");
  var buf_key_len = buf_key.length;
  var formatSinger = (rawData) => rawData.replace(/&/g, "\u3001");
  var createAesEncrypt = (buffer, mode, key, iv2) => {
    const cipher = createCipheriv(mode, key, iv2);
    return Buffer.concat([cipher.update(buffer), cipher.final()]);
  };
  var createAesDecrypt = (buffer, mode, key, iv2) => {
    const cipher = createDecipheriv(mode, key, iv2);
    return Buffer.concat([cipher.update(buffer), cipher.final()]);
  };
  var wbdCrypto = {
    aesMode: "aes-128-ecb",
    aesKey: Buffer.from([112, 87, 39, 61, 199, 250, 41, 191, 57, 68, 45, 114, 221, 94, 140, 228], "binary"),
    aesIv: "",
    appId: "y67sprxhhpws",
    decodeData(base64Result) {
      const data = Buffer.from(decodeURIComponent(base64Result), "base64");
      return JSON.parse(createAesDecrypt(data, this.aesMode, this.aesKey, this.aesIv).toString());
    },
    createSign(data, time) {
      const str = `${this.appId}${data}${time}`;
      return toMD5(str).toUpperCase();
    },
    buildParam(jsonData) {
      const data = Buffer.from(JSON.stringify(jsonData));
      const time = Date.now();
      const encodeData = createAesEncrypt(data, this.aesMode, this.aesKey, this.aesIv).toString("base64");
      const sign = this.createSign(encodeData, time);
      return `data=${encodeURIComponent(encodeData)}&time=${time}&appId=${this.appId}&sign=${sign}`;
    }
  };

  // lx/kw/musicSearch.js
  var musicSearch_default = {
    regExps: {
      mInfo: /level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/
    },
    limit: 30,
    total: 0,
    page: 0,
    allPage: 1,
    // cancelFn: null,
    musicSearch(str, page, limit) {
      const musicSearchRequestObj = httpFetch(`http://search.kuwo.cn/r.s?client=kt&all=${encodeURIComponent(str)}&pn=${page - 1}&rn=${limit}&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1`);
      return musicSearchRequestObj.promise;
    },
    // getImg(songId) {
    //   return httpGet(`http://player.kuwo.cn/webmusic/sj/dtflagdate?flag=6&rid=MUSIC_${songId}`)
    // },
    // getLrc(songId) {
    //   return httpGet(`http://mobile.kuwo.cn/mpage/html5/songinfoandlrc?mid=${songId}&flag=0`)
    // },
    handleResult(rawData) {
      const result = [];
      if (!rawData) return result;
      for (let i = 0; i < rawData.length; i++) {
        const info = rawData[i];
        let songId = info.MUSICRID.replace("MUSIC_", "");
        if (!info.N_MINFO) {
          console.log("N_MINFO is undefined");
          return null;
        }
        const types = [];
        const _types = {};
        let infoArr = info.N_MINFO.split(";");
        for (let info2 of infoArr) {
          info2 = info2.match(this.regExps.mInfo);
          if (info2) {
            switch (info2[2]) {
              case "4000":
                types.push({ type: "flac24bit", size: info2[4] });
                _types.flac24bit = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
              case "2000":
                types.push({ type: "flac", size: info2[4] });
                _types.flac = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
              case "320":
                types.push({ type: "320k", size: info2[4] });
                _types["320k"] = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
              case "128":
                types.push({ type: "128k", size: info2[4] });
                _types["128k"] = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
            }
          }
        }
        types.reverse();
        let interval = parseInt(info.DURATION);
        result.push({
          name: decodeName(info.SONGNAME),
          singer: formatSinger(decodeName(info.ARTIST)),
          source: "kw",
          // img = (info.album.name === '' || info.album.name === '空')
          //   ? `http://player.kuwo.cn/webmusic/sj/dtflagdate?flag=6&rid=MUSIC_160911.jpg`
          //   : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${info.album.mid}.jpg`
          songmid: songId,
          albumId: decodeName(info.ALBUMID || ""),
          interval: Number.isNaN(interval) ? 0 : formatPlayTime(interval),
          albumName: info.ALBUM ? decodeName(info.ALBUM) : "",
          lrc: null,
          img: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        });
      }
      return result;
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then(({ body: result }) => {
        if (!result || result.TOTAL !== "0" && result.SHOW === "0") return this.search(str, page, limit, ++retryNum);
        let list = this.handleResult(result.abslist);
        if (list == null) return this.search(str, page, limit, ++retryNum);
        this.total = parseInt(result.TOTAL);
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return Promise.resolve({
          list,
          allPage: this.allPage,
          total: this.total,
          limit,
          source: "kw"
        });
      });
    }
  };

  // lx/kg/musicSearch.js
  var musicSearch_default2 = {
    limit: 30,
    total: 0,
    page: 0,
    allPage: 1,
    musicSearch(str, page, limit) {
      const searchRequest = httpFetch(`http://songsearch.kugou.com/song_search_v2?platform=AndroidFilter&iscorrection=1&keyword=${encodeURIComponent(str)}&hifiquality=0&pagesize=${limit}&PrivilegeFilter=0&page=${page}`);
      return searchRequest.promise.then(({ body }) => body);
    },
    filterData(rawData) {
      const types = [];
      const _types = {};
      if (rawData.FileSize !== 0) {
        let size = sizeFormate(rawData.FileSize);
        types.push({ type: "128k", size, hash: rawData.FileHash });
        _types["128k"] = {
          size,
          hash: rawData.FileHash
        };
      }
      if (rawData.HQFileSize !== 0) {
        let size = sizeFormate(rawData.HQFileSize);
        types.push({ type: "320k", size, hash: rawData.HQFileHash });
        _types["320k"] = {
          size,
          hash: rawData.HQFileHash
        };
      }
      if (rawData.SQFileSize !== 0) {
        let size = sizeFormate(rawData.SQFileSize);
        types.push({ type: "flac", size, hash: rawData.SQFileHash });
        _types.flac = {
          size,
          hash: rawData.SQFileHash
        };
      }
      if (rawData.ResFileSize !== 0) {
        let size = sizeFormate(rawData.ResFileSize);
        types.push({ type: "flac24bit", size, hash: rawData.ResFileHash });
        _types.flac24bit = {
          size,
          hash: rawData.ResFileHash
        };
      }
      return {
        singer: decodeName(formatSingerName(rawData.Singers, "name")),
        name: decodeName(`${rawData.OriSongName}${rawData.Suffix ? ` ${rawData.Suffix}` : ""}`),
        albumName: decodeName(rawData.AlbumName),
        albumId: rawData.AlbumID,
        songmid: rawData.Audioid,
        albumAudioId: rawData.MixSongID,
        source: "kg",
        interval: formatPlayTime(rawData.Duration),
        _interval: rawData.Duration,
        img: null,
        lrc: null,
        otherSource: null,
        hash: rawData.FileHash,
        types,
        _types,
        typeUrl: {}
      };
    },
    handleResult(rawData) {
      let ids = /* @__PURE__ */ new Set();
      const list = [];
      rawData.forEach((item) => {
        const key = item.Audioid + item.FileHash;
        if (ids.has(key)) return;
        ids.add(key);
        list.push(this.filterData(item));
        for (const childItem of item.Grp) {
          const key2 = item.Audioid + item.FileHash;
          if (ids.has(key2)) continue;
          ids.add(key2);
          list.push(this.filterData(childItem));
        }
      });
      return list;
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then((result) => {
        if (!result || result.error_code !== 0) return this.search(str, page, limit, retryNum);
        let list = this.handleResult(result.data.lists);
        if (list == null) return this.search(str, page, limit, retryNum);
        this.total = result.data.total;
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return Promise.resolve({
          list,
          allPage: this.allPage,
          limit,
          total: this.total,
          source: "kg"
        });
      });
    }
  };

  // lx/tx/utils/crypto.js
  var PART_1_INDEXES = [23, 14, 6, 36, 16, 40, 7, 19];
  var PART_2_INDEXES = [16, 1, 32, 12, 19, 27, 8, 5];
  var SCRAMBLE_VALUES = [89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179];
  async function hashSHA1(data) {
    return node_builtins_default.createHash("sha1").update(data).digest("hex");
  }
  function pickHashByIdx(hash, indexes) {
    return indexes.map((idx) => hash[idx]).join("");
  }
  function base64Encode(data) {
    return Buffer.from(data).toString("base64").replace(/[\\/+=]/g, "");
  }
  async function zzcSign(text) {
    const hash = await hashSHA1(text);
    const part1 = pickHashByIdx(hash, PART_1_INDEXES);
    const part2 = pickHashByIdx(hash, PART_2_INDEXES);
    const part3 = SCRAMBLE_VALUES.map((value, i) => value ^ parseInt(hash.slice(i * 2, i * 2 + 2), 16));
    const b64Part = base64Encode(part3).replace(/[\\/+=]/g, "");
    return `zzc${part1}${b64Part}${part2}`.toLowerCase();
  }

  // lx/tx/utils/index.js
  var signRequest = async (data) => {
    const sign = await zzcSign(JSON.stringify(data));
    return httpFetch(`https://u.y.qq.com/cgi-bin/musics.fcg?sign=${sign}`, {
      method: "post",
      headers: {
        "User-Agent": "QQMusic 14090508(android 12)"
      },
      body: data
    }).promise;
  };

  // lx/tx/musicSearch.js
  var musicSearch_default3 = {
    limit: 50,
    total: 0,
    page: 0,
    allPage: 1,
    successCode: 0,
    musicSearch(str, page, limit, retryNum = 0) {
      if (retryNum > 5) return Promise.reject(new Error("\u641C\u7D22\u5931\u8D25"));
      const searchRequest = signRequest({
        comm: {
          _channelid: "0",
          _os_version: "6.2.9200-2",
          ct: "19",
          cv: "2151",
          guid: "1F70E520B2EAA7D25E11760783C53CA9",
          patch: "118",
          psrf_access_token_expiresAt: 0,
          psrf_qqaccess_token: "",
          psrf_qqopenid: "",
          psrf_qqunionid: "",
          tmeAppID: "qqmusic",
          tmeLoginType: 0,
          uin: "0",
          wid: "7223299733393904640"
        },
        "music.search.SearchCgiService": {
          module: "music.search.SearchCgiService",
          method: "DoSearchForQQMusicDesktop",
          param: {
            grp: 1,
            num_per_page: limit,
            page_num: page,
            query: str,
            remoteplace: "txt.newclient.top",
            search_type: 0,
            searchid: this.getSearchId()
          }
        }
      });
      return searchRequest.then(({ body }) => {
        const req = body?.["music.search.SearchCgiService"] ?? body?.req;
        if (!req || body.code != this.successCode || req.code != this.successCode) {
          return this.musicSearch(str, page, limit, ++retryNum);
        }
        return req.data;
      });
    },
    /**
     * PC 客户端版 searchid：32 位大写十六进制 GUID + 5 位补零随机数 = 37 字符。
     * 对应 QQ 音乐 PC 端 searchid 形状（服务端只需要唯一的会话 ID，形状一致即可）。
     */
    getSearchId() {
      let guid = "";
      for (let i = 0; i < 32; i++) guid += Math.floor(Math.random() * 16).toString(16);
      return guid.toUpperCase() + String(Math.floor(Math.random() * 1e5)).padStart(5, "0");
    },
    handleResult(rawList) {
      if (!rawList || !Array.isArray(rawList)) return [];
      const list = [];
      rawList.forEach((item) => {
        if (!item.file?.media_mid) return;
        let types = [];
        let _types = {};
        const file = item.file;
        if (file.size_128mp3 != 0) {
          let size = sizeFormate(file.size_128mp3);
          types.push({ type: "128k", size });
          _types["128k"] = {
            size
          };
        }
        if (file.size_320mp3 !== 0) {
          let size = sizeFormate(file.size_320mp3);
          types.push({ type: "320k", size });
          _types["320k"] = {
            size
          };
        }
        if (file.size_flac !== 0) {
          let size = sizeFormate(file.size_flac);
          types.push({ type: "flac", size });
          _types.flac = {
            size
          };
        }
        if (file.size_hires !== 0) {
          let size = sizeFormate(file.size_hires);
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        let albumId = "";
        let albumName = "";
        if (item.album) {
          albumName = item.album.name;
          albumId = item.album.mid;
        }
        list.push({
          singer: formatSingerName(item.singer, "name"),
          // name: item.name + (item.title_extra ?? ''),
          name: item.title,
          albumName,
          albumId,
          source: "tx",
          interval: item.interval ? formatPlayTime(item.interval) : null,
          songId: item.id,
          albumMid: item.album?.mid ?? "",
          strMediaMid: item.file.media_mid,
          songmid: item.mid,
          img: albumId === "" || albumId === "\u7A7A" ? item.singer?.length ? `https://y.gtimg.cn/music/photo_new/T001R500x500M000${item.singer[0].mid}.jpg` : "" : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumId}.jpg`,
          types,
          _types,
          typeUrl: {}
        });
      });
      return list;
    },
    search(str, page = 1, limit) {
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then(({ body, meta }) => {
        let list = this.handleResult(body.song.list);
        this.total = meta.sum;
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return Promise.resolve({
          list,
          allPage: this.allPage,
          limit,
          total: this.total,
          source: "tx"
        });
      });
    }
  };

  // lx/wy/utils/crypto.js
  var iv = Buffer.from("0102030405060708");
  var presetKey = Buffer.from("0CoJUm6Qyw8W8jud");
  var linuxapiKey = Buffer.from("rFgB&h#%2?^eDg:Q");
  var eapiKey = "e82ckenh8dichen8";
  var aesEncrypt = (buffer, mode, key, iv2) => {
    const cipher = createCipheriv(mode, key, iv2);
    return Buffer.concat([cipher.update(buffer), cipher.final()]);
  };
  var eapi = (url, object) => {
    const text = typeof object === "object" ? JSON.stringify(object) : object;
    const message = `nobody${url}use${text}md5forencrypt`;
    const digest = createHash("md5").update(message).digest("hex");
    const data = `${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}`;
    return {
      params: aesEncrypt(Buffer.from(data), "aes-128-ecb", eapiKey, "").toString("hex").toUpperCase()
    };
  };

  // lx/wy/utils/index.js
  var eapiRequest = (url, data) => {
    return httpFetch("http://interface.music.163.com/eapi/batch", {
      method: "post",
      headers: {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
        origin: "https://music.163.com"
        // cookie: 'os=pc; deviceId=A9C064BB4584D038B1565B58CB05F95290998EE8B025AA2D07AE; osver=Microsoft-Windows-10-Home-China-build-19043-64bit; appver=2.5.2.197409; channel=netease; MUSIC_A=37a11f2eb9de9930cad479b2ad495b0e4c982367fb6f909d9a3f18f876c6b49faddb3081250c4980dd7e19d4bd9bf384e004602712cf2b2b8efaafaab164268a00b47359f85f22705cc95cb6180f3aee40f5be1ebf3148d888aa2d90636647d0c3061cd18d77b7a0; __csrf=05b50d54082694f945d7de75c210ef94; mode=Z7M-KP5(7)GZ; NMTID=00OZLp2VVgq9QdwokUgq3XNfOddQyIAAAF_6i8eJg; ntes_kaola_ad=1',
      },
      form: eapi(url, data)
    });
  };

  // lx/wy/musicSearch.js
  var musicSearch_default4 = {
    limit: 30,
    total: 0,
    page: 0,
    allPage: 1,
    musicSearch(str, page, limit) {
      const searchRequest = eapiRequest("/api/search/song/list/page", {
        keyword: str,
        needCorrect: "1",
        channel: "typing",
        offset: limit * (page - 1),
        scene: "normal",
        total: page == 1,
        limit
      });
      return searchRequest.promise.then(({ body }) => body);
    },
    getSinger(singers) {
      let arr = [];
      singers.forEach((singer) => {
        arr.push(singer.name);
      });
      return arr.join("\u3001");
    },
    handleResult(rawList) {
      if (!rawList) return [];
      return rawList.map((item) => {
        item = item.baseInfo.simpleSongData;
        const types = [];
        const _types = {};
        let size;
        if (item.privilege.maxBrLevel == "hires") {
          size = item.hr ? sizeFormate(item.hr.size) : null;
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        switch (item.privilege.maxbr) {
          case 999e3:
            size = item.sq ? sizeFormate(item.sq.size) : null;
            types.push({ type: "flac", size });
            _types.flac = {
              size
            };
          case 32e4:
            size = item.h ? sizeFormate(item.h.size) : null;
            types.push({ type: "320k", size });
            _types["320k"] = {
              size
            };
          case 192e3:
          case 128e3:
            size = item.l ? sizeFormate(item.l.size) : null;
            types.push({ type: "128k", size });
            _types["128k"] = {
              size
            };
        }
        types.reverse();
        return {
          singer: this.getSinger(item.ar),
          name: item.name,
          albumName: item.al.name,
          albumId: item.al.id,
          source: "wy",
          interval: formatPlayTime(item.dt / 1e3),
          songmid: item.id,
          img: item.al.picUrl,
          lrc: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then((result) => {
        if (!result || result.code !== 200) return this.search(str, page, limit, retryNum);
        let list = this.handleResult(result.data.resources || []);
        if (list == null) return this.search(str, page, limit, retryNum);
        this.total = result.data.totalCount || 0;
        this.page = page;
        this.allPage = Math.ceil(this.total / this.limit);
        return {
          list,
          allPage: this.allPage,
          limit: this.limit,
          total: this.total,
          source: "wy"
        };
      });
    }
  };

  // lx/mg/musicSearch.js
  var createSignature = (time, str) => {
    const deviceId = "963B7AA0D21511ED807EE5846EC87D20";
    const signatureMd5 = "6cdc72a439cef99a3418d2a78aa28c73";
    const sign = toMD5(`${str}${signatureMd5}yyapp2d16148780a1dcc7408e06336b98cfd50${deviceId}${time}`);
    return { sign, deviceId };
  };
  var musicSearch_default5 = {
    limit: 20,
    total: 0,
    page: 0,
    allPage: 1,
    // 旧版API
    // musicSearch(str, page, limit) {
    //   const searchRequest = httpFetch(`http://pd.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?ua=Android_migu&version=5.0.1&text=${encodeURIComponent(str)}&pageNo=${page}&pageSize=${limit}&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A0%2C%22mvSong%22%3A0%2C%22songlist%22%3A0%2C%22bestShow%22%3A1%7D`, {
    // searchRequest = httpFetch(`http://pd.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?ua=Android_migu&version=5.0.1&text=${encodeURIComponent(str)}&pageNo=${page}&pageSize=${limit}&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A0%2C%22mvSong%22%3A0%2C%22songlist%22%3A0%2C%22bestShow%22%3A1%7D`, {
    // searchRequest = httpFetch(`http://jadeite.migu.cn:7090/music_search/v2/search/searchAll?sid=4f87090d01c84984a11976b828e2b02c18946be88a6b4c47bcdc92fbd40762db&isCorrect=1&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=${limit}&text=${encodeURIComponent(str)}&pageNo=${page}&sort=0`, {
    // searchRequest = httpFetch(`https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do?isCopyright=1&isCorrect=1&pageNo=${page}&pageSize=${limit}&searchSwitch={%22song%22:1,%22album%22:0,%22singer%22:0,%22tagSong%22:0,%22mvSong%22:0,%22songlist%22:0,%22bestShow%22:0}&sort=0&text=${encodeURIComponent(str)}`)
    //   // searchRequest = httpFetch(`http://jadeite.migu.cn:7090/music_search/v2/search/searchAll?sid=4f87090d01c84984a11976b828e2b02c18946be88a6b4c47bcdc92fbd40762db&isCorrect=1&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=${limit}&text=${encodeURIComponent(str)}&pageNo=${page}&sort=0`, {
    //     headers: {
    //       // sign: 'c3b7ae985e2206e97f1b2de8f88691e2',
    //       // timestamp: 1578225871982,
    //       // appId: 'yyapp2',
    //       // mode: 'android',
    //       // ua: 'Android_migu',
    //       // version: '6.9.4',
    //       osVersion: 'android 7.0',
    //       'User-Agent': 'okhttp/3.9.1',
    //     },
    //   })
    //   // searchRequest = httpFetch(`https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do?isCopyright=1&isCorrect=1&pageNo=${page}&pageSize=${limit}&searchSwitch={%22song%22:1,%22album%22:0,%22singer%22:0,%22tagSong%22:0,%22mvSong%22:0,%22songlist%22:0,%22bestShow%22:0}&sort=0&text=${encodeURIComponent(str)}`)
    //   return searchRequest.promise.then(({ body }) => body)
    // },
    // handleResult(rawData) {
    //   // console.log(rawData)
    //   let ids = new Set()
    //   const list = []
    //   rawData.forEach(item => {
    //     if (ids.has(item.id)) return
    //     ids.add(item.id)
    //     const types = []
    //     const _types = {}
    //     item.newRateFormats && item.newRateFormats.forEach(type => {
    //       let size
    //       switch (type.formatType) {
    //         case 'PQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: '128k', size })
    //           _types['128k'] = {
    //             size,
    //           }
    //           break
    //         case 'HQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: '320k', size })
    //           _types['320k'] = {
    //             size,
    //           }
    //           break
    //         case 'SQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: 'flac', size })
    //           _types.flac = {
    //             size,
    //           }
    //           break
    //         case 'ZQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: 'flac24bit', size })
    //           _types.flac24bit = {
    //             size,
    //           }
    //           break
    //       }
    //     })
    //     const albumNInfo = item.albums && item.albums.length
    //       ? {
    //           id: item.albums[0].id,
    //           name: item.albums[0].name,
    //         }
    //       : {}
    //     list.push({
    //       singer: this.getSinger(item.singers),
    //       name: item.name,
    //       albumName: albumNInfo.name,
    //       albumId: albumNInfo.id,
    //       songmid: item.songId,
    //       copyrightId: item.copyrightId,
    //       source: 'mg',
    //       interval: null,
    //       img: item.imgItems && item.imgItems.length ? item.imgItems[0].img : null,
    //       lrc: null,
    //       lrcUrl: item.lyricUrl,
    //       mrcUrl: item.mrcurl,
    //       trcUrl: item.trcUrl,
    //       otherSource: null,
    //       types,
    //       _types,
    //       typeUrl: {},
    //     })
    //   })
    //   return list
    // },
    musicSearch(str, page, limit) {
      const time = Date.now().toString();
      const signData = createSignature(time, str);
      const searchRequest = httpFetch(`https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=${limit}&text=${encodeURIComponent(str)}&pageNo=${page}&sort=0&sid=USS`, {
        headers: {
          uiVersion: "A_music_3.6.1",
          deviceId: signData.deviceId,
          timestamp: time,
          sign: signData.sign,
          channel: "0146921",
          "User-Agent": "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
        }
      });
      return searchRequest.promise.then(({ body }) => body);
    },
    filterData(rawData) {
      const list = [];
      const ids = /* @__PURE__ */ new Set();
      rawData.forEach((item) => {
        item.forEach((data) => {
          if (!data.songId || !data.copyrightId || ids.has(data.copyrightId)) return;
          ids.add(data.copyrightId);
          const types = [];
          const _types = {};
          data.audioFormats && data.audioFormats.forEach((type) => {
            let size;
            switch (type.formatType) {
              case "PQ":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "128k", size });
                _types["128k"] = {
                  size
                };
                break;
              case "HQ":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "320k", size });
                _types["320k"] = {
                  size
                };
                break;
              case "SQ":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "flac", size });
                _types.flac = {
                  size
                };
                break;
              case "ZQ24":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "flac24bit", size });
                _types.flac24bit = {
                  size
                };
                break;
            }
          });
          let img = data.img3 || data.img2 || data.img1 || null;
          if (img && !/https?:/.test(data.img3)) img = "http://d.musicapp.migu.cn" + img;
          list.push({
            singer: formatSingerName(data.singerList),
            name: data.name,
            albumName: data.album,
            albumId: data.albumId,
            songmid: data.songId,
            copyrightId: data.copyrightId,
            source: "mg",
            interval: formatPlayTime(data.duration),
            img,
            lrc: null,
            lrcUrl: data.lrcUrl,
            mrcUrl: data.mrcurl,
            trcUrl: data.trcUrl,
            types,
            _types,
            typeUrl: {}
          });
        });
      });
      return list;
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then((result) => {
        if (!result || result.code !== "000000") return Promise.reject(new Error(result ? result.info : "\u641C\u7D22\u5931\u8D25"));
        const songResultData = result.songResultData || { resultList: [], totalCount: 0 };
        let list = this.filterData(songResultData.resultList);
        if (list == null) return this.search(str, page, limit, retryNum);
        this.total = parseInt(songResultData.totalCount);
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return {
          list,
          allPage: this.allPage,
          limit,
          total: this.total,
          source: "mg"
        };
      });
    }
  };

  // entry.js
  var providers = { kw: musicSearch_default, kg: musicSearch_default2, tx: musicSearch_default3, wy: musicSearch_default4, mg: musicSearch_default5 };
  function normalize(sourceId, raw) {
    const list = (raw.list || []).map((item) => {
      const qualitys = {};
      if (item._types) {
        for (const q of Object.keys(item._types)) {
          qualitys[q] = { type: q, size: item._types[q].size, hash: item._types[q].hash || null };
        }
      }
      return {
        id: sourceId + "_" + (item.songmid != null ? item.songmid : ""),
        source: sourceId,
        songmid: String(item.songmid != null ? item.songmid : ""),
        name: item.name || "",
        singer: item.singer || "",
        albumName: item.albumName || "",
        albumId: String(item.albumId != null ? item.albumId : ""),
        interval: item.interval || "00:00",
        intervalSeconds: 0,
        img: item.img || null,
        qualitys,
        extra: {
          hash: item.hash || "",
          albumAudioId: String(item.albumAudioId != null ? item.albumAudioId : ""),
          strMediaMid: item.strMediaMid || "",
          albumMid: item.albumMid || "",
          songId: String(item.songId != null ? item.songId : "")
        }
      };
    });
    return {
      list,
      total: raw.total || list.length,
      page: raw.page || 1,
      allPage: raw.allPage || 1,
      source: sourceId
    };
  }
  var wrapped = {};
  Object.keys(providers).forEach((id) => {
    const p = providers[id];
    wrapped[id] = {
      search: async (keyword, page, limit) => {
        const raw = await p.search(keyword, page, limit == null ? p.limit : limit);
        return normalize(id, raw);
      }
    };
    window.__lx_register_provider__(id, wrapped[id]);
  });
  window.__lx_search_ready__ = true;
})();
