TTM {
	var <tweets;
	var <dictesen;
	var <sounds;
	var punct;
	var task;

	*new { arg path;
		^super.new.init(path);
	}

	init { arg p;
		tweets = Tweets.new(p);
		dictesen = Dictesen.new(p);
		sounds = Sounds.new(p);

		punct = [ // very basic, because unicode lack
			".", ",", ";", ":", "'", "`", "\"",
			"(", ")", "[", "]", "<", ">", "{", "}",
			"!", "¡", "?", "¿", "\\", "/", "|",
			"*", "#", "@", "$", "-", "+"
		];

		this.prInitTask;
	}

	start {
		task.start;
	}

	stop {
		task.stop;
	}

	getWords { arg string;
		var words;
		punct.do({ arg i; string = string.replace(i, " "); });
		words = string.split($ );
		words.removeAllSuchThat({ arg i; i.isEmpty; });
		^words;
	}

	prInitTask {
		task = Task({
			var lastTweets, lastEsWords, lastEnWords, lastSounds;

			inf.do({
				lastTweets = lastEsWords = lastEnWords = lastSounds = nil;
				lastTweets = tweets.search; // returns the last ones/nil
				lastTweets.do({ arg i;
					lastEsWords = lastEsWords ++ this.getWords(i.last);
					lastEsWords.postln;
				});

				lastEsWords.do({ arg i;
					var word;
					word = dictesen.addWord(i); // returns englishw/nil
					if(word.notNil, {
						lastEnWords = lastEnWords.add(word);
					}, {
						lastEsWords = lastEsWords.remove(i);
					});
				});

				lastEnWords.do({ arg i;
					sounds.search(i) !? { arg list; // last sounds/nil
						lastSounds = lastSounds ++ list;
					};
				});

				// send data, trigger something ...
				$-.dup(20).join.postln;
				lastTweets.postln;
				lastEsWords.postln;
				lastEnWords.postln;
				lastSounds.postln;
				15.wait;
			});
		});
	}
}

TTMDB {
	var <data;
	var <path;
	var <fileName;

	init { arg p, f;
		var dataFile;

		path = (p ? "~/Desktop/").standardizePath;
		fileName = f ? "file.db";
		dataFile = File(path +/+ fileName, "r");
		if(dataFile.isOpen, {
			data = dataFile.readAllString.compile.value;
			dataFile.close;
		});
		this.initData;
	}

	initData {
		// always check if nil
		data = data ? [];
	}

	write { // fix: siempre vuelve a escribir todo
		var dataFile;

		dataFile = File(path +/+ fileName, "w");
		dataFile.write(data.asCompileString);
		dataFile.close;
	}
}

Sounds : TTMDB {
	var <token;

	*new { arg path, file = "sounds.db";
		^super.new.init(path, file)
	}

	setToken { arg t;
		token = t;
		Freesound.authType = "token";
		Freesound.token = token;
	}

	search { arg word, nfiles = 2;
		var fspager, indx, retrieved;

		fspager = FSSound.textSearchSync(
			query: word, filter: "type:wav",
			params: (page: 2)
		);
		if(fspager.dict.includesKey("count").not, { ^nil }); //{"detail":"Not found"}
		if(fspager.count < nfiles, { nfiles = fspager.count });

		indx = (0..(fspager.results.size-1)).scramble[0..(nfiles-1)];
		indx.do({ arg i;
			var fssound = fspager.at(i);
			var errorCode;

			if(data.flat.includes(fssound.id.asSymbol).not, {
				errorCode = fssound.retrievePreviewSync(
					path +/+ "fssounds/",
					quality: "hq",
					format: "ogg"
				);
				if(errorCode == 0, {
					retrieved = retrieved.add([
						word.asSymbol,
						fssound.id.asSymbol,
						fssound.previewFilename,
						Date.getDate.asSortableString
					]);
					data = data.add(retrieved.last);
				});
			});
		});
		this.write;
		^retrieved;
	}
}

Tweets : TTMDB {
	var <query;
	var <csvfile;

	*new { arg path, file = "tweets.db";
		^super.new.init(path, file)
	}

	buildQuery { arg since, hashtag ...or;
		var n = 20;
		// "#hashtag"
		// "2015-12-21"
		// "@a", "filter", ...
		query = "since:" ++ since;
		query = query + hashtag;
		or.do { arg i; query = query + "OR" + i };
		query = "t search all \"%\" -n % --csv".format(query, n);
	}

	search {
		var existingIDs, append;

		systemCmd(query + ">" + "/tmp/tmp.csv");
		// check nil/empty?
		csvfile = CSVFileReader.read("/tmp/tmp.csv"); // not working right
		csvfile = csvfile.drop(1);
		csvfile = csvfile.collect({ arg i; i[0] = i[0].asSymbol; i });
		existingIDs = data.flop.at(0);
		append = csvfile.reject({ arg i;
			existingIDs.includes(i.at(0))
		});
		data = data ++ append;
		this.write;
		if(append.isEmpty, { ^nil }, { ^append });
	}
}

Dictesen : TTMDB {
	*new { arg path, file = "dictesen.db";
		^super.new.init(path, file)
	}

	initData {
		data = data ? Dictionary.new;
	}

	addWord { arg palabra;
		var word;

		if(data.includesKey(palabra).not, {
			word = this.translate(palabra, "es-en");
			if(word.first == $*) { // está en inglés?
				word = word.replace($*, "");
				palabra = this.translate(word, "en-es"); // si no, esto da *
			};
			if((palabra.first != $*) and: { this.isValidWord(word) }) {
				this.data.put(palabra, word);
				this.write;
				^word;
			};
		});
		^nil;
	}

	translate { arg palabra, direction = "es-en";
		^"echo \"%\" | apertium %".format(palabra, direction)
		.unixCmdGetStdOut.replace($ , "")
		.replace($\n, "")
		.toLower;
	}

	isValidWord { arg word;
		// noun, standard verb, adjective
		var poe = ["<n>", "<vblex>", "<adj>"];
		// http://wiki.apertium.org/wiki/Monodix_basics
		// http://wiki.apertium.org/wiki/List_of_symbols
		var analysis;
		var dict = "/usr/share/apertium/apertium-en-es/en-es.automorf.bin";

		analysis = "echo \"%\" | lt-proc -a %".format(word, dict)
		.unixCmdGetStdOut
		.postln;

		poe.do({ arg i; if(analysis.contains(i), { ^true }) });
		^false;
	}
}


// Freesound quark sync call

+ FSReq {
	getSync { arg objClass;
		cmd.systemCmd;
		^objClass.new(
			File(filePath,"r").readAllString.postln;
			Freesound.parseFunc.value(
				File(filePath,"r").readAllString
			)
		); // return the object
	}

	*retrieveSync { arg uri, path;
		var cmd;
		cmd = "curl -H '%' '%'>'%'".format(FSReq.getHeader, uri, path);
		cmd.postln;
		^cmd.systemCmd; // return Error code
	}
}

+ FSSound {
	*textSearchSync { arg query, filter, sort, params, action;
		params = FSSound.initParams(params);
		params.putAll(('query' : query, 'filter' : filter, 'sort' : sort));
		^FSReq.new(Freesound.uri(\TEXT_SEARCH),params).getSync(FSPager);
		// return the object
	}

	retrievePreviewSync { arg path, quality = "hq", format = "ogg";
		var key = "%-%-%".format("preview",quality,format);
		^FSReq.retrieveSync(
			this.previews.dict[key],
			path ++ this.previewFilename(format)
		); // return Error code
	}
}
