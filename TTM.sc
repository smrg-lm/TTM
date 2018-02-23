
T2M {
	var <path;
	var <soundsFolder = "fssounds";
	var <tweets;
	var <apertium;
	var <sounds;
	var <wplayer;
	var <wprinter;

	var punct;

	*new { arg path, folder = "T2M";
		^super.new.init(path, folder);
	}

	init { arg p, f;
		this.initPath(p, f);
		if(path.isNil, { ^nil });

		tweets = Tweets.new(path);
		apertium = Apertium.new(j: 4);
		sounds = Sounds.new(path, soundsFolder: soundsFolder);
		wplayer = WordPlayer(this, Server.default);
		wprinter = WordPrinter.new;

		punct = [ // very basic, because unicode lack
			".", ",", ";", ":", "'", "`", "\"",
			"(", ")", "[", "]", "<", ">", "{", "}",
			"!", "¡", "?", "¿", "\\", "/", "|",
			"*", "#", "@", "$", "-", "+",
			"\n", "\t", "\f", "http", "https" // ...
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

	search { // ** arg param = 2;
		tweets.search({ arg tweetsList;
			if(tweetsList.notNil, {
				tweetsList.do({ arg i;
					//"Nuevo tweet: %".format(i.last).warn;
					wprinter.print(" [ % | % ] ".format(i[2], i[3]));
					// ** lastWords = lastWords ++ this.getWords(i.last);
					this.extractWords(i.last, { arg words;
						words.do({ arg word;
							sounds.search(word, 15.rand, action: { arg sound;
								if(sound.notNil, {
									//"Playing word: % sound: %\n".format(
									//	word.toUpper, sound).warn;
									wprinter.print(" [ % | % ] "
										.format(word.toUpper, sound[2]));
									wplayer.pushSound(sound, 30, 0.4); // ver lifeTime
								});
							});
						});
					});
				});
			}, {
				"No se encontraron nuevos tweets".warn;
				//this.planB;
			});
		});
	}

	//getWords { arg string;
	extractWords { arg string, action;
		var blanks, error = "\"status\": \"error\"";

		// filter all punct...
		punct.do({ arg i; string = string.replace(i, " "); });
		blanks = string.findRegexp(" {2,}");
		blanks.do({ arg i; string = string.replace(i[1], " ") });

		this.apertium.detectLanguage(string, { arg str;
			var max = -1, lang, analizeMorph;

			// se requieren los paquetes de los lenguajes a detectar
			// apertium-eng apertium-spa, usa el detector nativo, no cld2
			// aunque la interfaz no debería cambiar
			//"*** LANG: ".post; str.postln;

			if(str.contains(error), { str.postln; action.value; }, {
				// calc lang
				str = str.parseYAML;
				str.keysValuesDo({ arg key, value;
					if(value.asFloat > max, {
						max = value.postln;
						lang = key;
					});
				});

				// analyze morph and collect valid words by type
				analizeMorph = {
					this.apertium.perWord(string, "eng", action: { arg str;
						var words, rules = ["<n>", "<vblex>", "<adj>"];

						if(str.contains(error), { str.postln; action.value; }, {
							str = str.parseYAML;
							str.do({ arg i;
								block { arg break;
									rules.do({ arg rule;
										// *** faltaría checkear si la palabra existe es es con asterisco? ***
										if(i["morph"].flat.join.contains(rule), {
											words = words.add(i["input"]);
											break.value;
										})
									});
								};
							});
							action.value(words); // return
						});
					});
				};

				// translate to english if needed
				if(lang != "eng", {
					this.apertium.translate(string, lang++"|eng", { arg str;
						if(str.contains(error), { str.postln; action.value; }, {
							str = str.parseYAML;
							string = str["responseData"]["translatedText"];
							analizeMorph.value(string);
						});
					});
				}, {
					analizeMorph.value(string);
				});
			});
		});
	}
}
