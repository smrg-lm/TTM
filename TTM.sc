TTM {
	var <path;
	var <soundsFolder = "fssounds";
	var <tweets;
	var <dictesen;
	var <sounds;
	var punct;

	*new { arg path, folder = "TTM";
		^super.new.init(path, folder);
	}

	init { arg p, f;
		this.initPath(p, f);
		if(path.isNil, { ^nil });

		tweets = Tweets.new(path);
		dictesen = Dictesen.new(path);
		sounds = Sounds.new(path, soundsFolder: soundsFolder);

		punct = [ // very basic, because unicode lack
			".", ",", ";", ":", "'", "`", "\"",
			"(", ")", "[", "]", "<", ">", "{", "}",
			"!", "¡", "?", "¿", "\\", "/", "|",
			"*", "#", "@", "$", "-", "+",
			"\n", "\t", "\f"
		];
	}

	initPath { arg p, f;
		var newPath;
		path = (p ? Platform.userHomeDir).standardizePath;

		if(File.exists(path), {
			if(PathName(path).isFile.not, {
				newPath = path +/+ f;
				if(File.exists(newPath).not, {
					File.mkdir(newPath +/+ soundsFolder);
					"INFO (%): Creating DB at %".format(this, newPath).inform;
					path = newPath;
				}, {
					if(File.exists(newPath +/+ soundsFolder).not, {
						File.mkdir(newPath +/+ soundsFolder)
					});
					"INFO (%): Using existing DB at %".format(this, newPath).inform;
					path = newPath;
				})
			}, {
				"Path % is an existing file".format(path).error;
				path = nil;
			});
		}, {
			"Path % does not exist".format(path).error;
			path = nil;
		});
	}

	search { arg param = 2;
		var lastTweets, lastWords, lastSounds;

		tweets.search({ arg tweetsList; // tweets/nil
			lastTweets = tweetsList;

			if(lastTweets.notNil, {
				lastTweets.do({ arg i;
					lastWords = lastWords ++ this.getWords(i.last);
				});
				lastWords.do({ arg palabra;
					dictesen.addWord(palabra, action: { arg word; //word(en/es)/nil
						if(word.notNil, {
							sounds.search(word, 15.rand, action: { arg soundList; // last sounds/nil
								var sound;
								if(soundList.notNil, {
									lastSounds = lastSounds ++ soundList;
									sound = soundList.at(0); // test
									this.playWord(word, sound); // test
								});
							});
						});
					});
				});
			}, {
				"No se encontraron nuevos tweets".warn;
			});

		});
	}

	playWord { arg word, sound; // test
		fork {
			"Playing word: % sound: %\n".format(word.toUpper, sound).warn;
			Buffer.read(
				Server.default,
				(this.path +/+ this.soundsFolder +/+ sound.at(2)).postln,
				action: { arg buf;
					buf.play(true, 0.25);
				}
			);
		}
	}

	getWords { arg string;
		var words;
		punct.do({ arg i; string = string.replace(i, " "); });
		words = string.split($ );
		words.removeAllSuchThat({ arg i; i.isEmpty; });
		^words;
	}
}

TTMDB {
	var <data;
	var <path;
	var <fileName;

	init { arg p, f;
		var dataFile;

		path = (p ? Platform.userHomeDir).standardizePath;
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

	write { // fix: siempre vuelve a escribir todo, MUTEX?
		var dataFile;

		dataFile = File(path +/+ fileName, "w");
		dataFile.write(data.asCompileString);
		dataFile.close;
	}
}

Sounds : TTMDB {
	var <token;
	var <soundsFolder;

	*new { arg path, file = "sounds.db", soundsFolder = "fssounds";
		^super.new.init(path, file, soundsFolder);
	}

	init { arg p, f, s;
		super.init(p, f);
		soundsFolder = s;
	}

	setToken { arg t;
		token = t;
		Freesound.authType = "token";
		Freesound.token = token;
	}

	search { arg word, index = 0, action;
		var fspager, indx, retrieved;

		FSSound.textSearch(
			query: word, filter: "type:wav duration:[20 TO 120]", sort: "duration_asc",
			params: (page: 2), action: { arg fspager;
				if(fspager.dict.includesKey("results").not or:
					{ fspager.results.size <= index } or:
					{ fspager.results.size < 1 }, {
						"%: no se encontraron resultados".format(this).warn;
						action.value(nil);
					}, {
						var fssound = fspager.at(index);
						var filePath = path +/+ soundsFolder +/+ fssound.previewFilename;

						if(data.flat.includes(fssound.id.asSymbol).not, {
							fssound.retrievePreview(
								path +/+ soundsFolder,
								action: {
									if(File.exists(filePath), {
										retrieved = retrieved.add([
											word.asSymbol,
											fssound.id.asSymbol,
											fssound.previewFilename,
											Date.getDate.asSortableString
										]);
										data = data.add(retrieved.last);
										this.write; // MUTEX
										action.value(retrieved);
									}, {
										"%: error en la descarga".format(this).warn;
									});
								},
								quality: "hq",
								format: "ogg"
							);
						}, {
							"%: el archivo ya fue descargado".format(this).warn;
						});
				});
		});
	}
}

Tweets : TTMDB {
	var <query;
	var <csvfile;

	*new { arg path, file = "tweets.db";
		^super.new.init(path, file)
	}

	buildQuery { arg /*since*/ n = 1, hashtag ...or;
		//var n = 20;
		// "#hashtag"
		// "2015-12-21"
		// "@a", "filter", ...
		query = ""; //"since:" ++ Date.getDate.format("%Y-%m-%d");
		query = query + hashtag;
		or.do { arg i; query = query + "OR" + i };
		query = "t search all \"%\" -n % --csv".format(query, n);
	}

	search { arg action;
		var tmpFile = PathName.tmp +/+ "tweets%".format(UniqueID.next);

		(query + ">" + tmpFile).unixCmd({ arg res, pid;
			var existingIDs, append;

			csvfile = CSVFileReader.read(tmpFile);
			if(csvfile.isNil, {
				"%: No new tweets".format(this).inform;
				action.value(nil);
			}, {
				csvfile = csvfile.drop(1);
				csvfile = csvfile.collect({ arg i; i[0] = i[0].asSymbol; i });
				existingIDs = data.flop.at(0);
				append = csvfile.reject({ arg i;
					existingIDs.includes(i.at(0))
				});
				data = data ++ append;
				this.write;
				if(append.isEmpty, {
					action.value(nil)
				}, {
					action.value(append)
				});
			});
		});
	}
}

Dictesen : TTMDB {
	*new { arg path, file = "dictesen.db";
		^super.new.init(path, file)
	}

	initData {
		data = data ? Dictionary.new;
	}

	addWord { arg palabra, direction = "es-en",
		rules = ["<n>", "<vblex>", "<adj>"], action;

		if(data.includesKey(palabra).not, {
			this.translate(palabra, direction, { arg word;
				if(word.notNil, {
					this.checkValidWord(
						word, direction.split($-).reverse.join($-),
						rules, { arg valid;
							if(valid, {
								this.data.put(palabra, word);
								this.write; // MUTEX
								action.value(word);
							}, {
								action.value(nil);
							});
					});
				}, {
					action.value(nil);
				});
			});
		}, {
			action.value(palabra); // ver qué hacer.
		});
	}

	translate { arg palabra, direction = "es-en", action;
		var tmpFile = PathName.tmp +/+ "dictesen%".format(UniqueID.next);

		"echo \"%\" | apertium % > %".format(palabra, direction, tmpFile)
		.unixCmd({ arg res, pid;
			var file, cmdOut;
			file = File.open(tmpFile, "r");
			cmdOut = file.readAllString;
			file.close;
			if(cmdOut.first != $*, {
				action.value(
					cmdOut
					//.replace($ , "") // puede devolver una frase verbal
					.replace($\n, "")
					.toLower
				);
			}, { action.value(nil) });
		});
	}

	checkValidWord { arg word, direction = "en-es",
		rules = ["<n>", "<vblex>", "<adj>"], action;

		// noun, standard verb, adjective
		// http://wiki.apertium.org/wiki/Monodix_basics
		// http://wiki.apertium.org/wiki/List_of_symbols

		var dict = "/usr/share/apertium/apertium-en-es/%.automorf.bin"
		.format(direction);
		var tmpFile = PathName.tmp +/+ "dictesen%".format(UniqueID.next);

		"echo \"%\" | lt-proc -a % > %".format(word, dict, tmpFile)
		.unixCmd({ arg res, pid;
			var file, cmdOut, valid = false;
			file = File.open(tmpFile, "r");
			cmdOut = file.readAllString;
			file.close;
			block { arg break;
				rules.do({ arg i;
					if(cmdOut.contains(i), {
						valid = true;
						break.value;
					})
				});
			};
			action.value(valid);
		});
	}
}


+ FSSound {
	retrievePreview{|path, action, quality = "hq", format = "ogg"|
		var key = "%-%-%".format("preview",quality,format);
		FSReq.retrieve(
			this.previews.dict[key],
			path +/+ this.previewFilename(format), // fixed
			action
		);
	}
}

+ CSVFileReader {
	next {
		var c, record, string = String.new;
		var inQuotes = false; // *

		while {
			c = stream.getChar;
			c.notNil
		} {
			if (inQuotes.not and: { c == delimiter }) { // *
				if (skipBlanks.not or: { string.size > 0 }) {
					record = record.add(string);
					string = String.new;
				}
			} {
				if (inQuotes.not and: { c == $\n or: { c == $\r } }) { // *
					record = record.add(string);
					string = String.new;
					if (skipEmptyLines.not or: { (record != [ "" ]) }) {
						^record
					};
					record = nil;
				} {
					if(inQuotes.not and: { c == $\" }) {
						inQuotes = true;
					} {
						if(c == $\") {
							inQuotes = false;
						}
					}; // *
					string = string.add(c);
				}
			}
		};
		if (string.notEmpty) { ^record.add(string) };
		^record;
	}
}
