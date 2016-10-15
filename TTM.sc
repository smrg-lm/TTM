TTM {
	var <path;
	var <soundsFolder = "fssounds";
	var <tweets;
	var <dictesen;
	var <sounds;
	var <wplayer;
	var <wprinter;

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

	search { arg param = 2;
		var lastTweets, lastWords, lastSounds;

		tweets.search({ arg tweetsList;
			lastTweets = tweetsList;

			if(lastTweets.notNil, {
				lastTweets.do({ arg i;
					//"Nuevo tweet: %".format(i.last).warn;
					wprinter.print(" [ % | % ] ".format(i[2], i[3]));
					lastWords = lastWords ++ this.getWords(i.last);
				});
				lastWords.do({ arg palabra;
					//"Palabra a traducir: %".format(palabra.toUpper).warn;
					dictesen.addWord(palabra, action: { arg word; //word(en/es)
						if(word.notNil, {
							sounds.search(word, 15.rand, action: { arg sound;
								if(sound.notNil, {
									lastSounds = lastSounds.add(sound);
									//"Playing word: % sound: %\n".format(
									//	word.toUpper, sound).warn;
									wprinter.print(" [ % | % ] "
										.format(palabra.toUpper, sound[2]));
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

	nuevaPalabra { arg palabra;
		dictesen.addWord(palabra, action: { arg word; //word(en/es)
			if(word.notNil, {
				sounds.search(word, 15.rand, action: { arg sound;
					if(sound.notNil, {
						wprinter.print(" [ % | % ] "
							.format(palabra, sound[2]));
						wplayer.pushSound(sound, 30, 0.4); // ver lifeTime
					});
				});
			});
		});
	}

	planB { arg palabra;
		var word, indx, sound;

		if(palabra.isNil, {
			// Plan B :-) :-| :-/ :-( :'S
			sounds.data.scramble[0..2].do({ arg i;
				wprinter.print(" [ | % ] ".format(i[2]));
				wplayer.pushSound(i, 5, 0.1); // ver lifeTime
			});
		}, {
			word = dictesen.at(palabra);
			if(word.notNil, {
				indx = sounds.data.flop.at(0).indexOf(word); // english words
				sound = sounds.data.at(indx div: 4);
				wprinter.print(
					" [ % | % ] ".format(palabra, sound[2])
				);
				wplayer.pushSound(sound, 30, 0.1);
			}, {
				"La palabra % no está en el diccionario".format(palabra.toUpper).warn;
			});
		});
	}

	getWords { arg string;
		var words;
		punct.do({ arg i; string = string.replace(i, " "); });
		words = string.split($ );
		words.removeAllSuchThat({ arg i; i.isEmpty; });
		^words;
	}
}

WordPrinter {
	var <tty;
	var pipe;
	var printLoop;
	var queue;
	var <>slow = 0.1;
	var <>fast = 0.02;

	*new { arg tty = "/dev/pts/0";
		^super.new.init(tty);
	}

	init { arg t;
		tty = t;
		this.tty_(t);
		queue = List.new;
	}

	close {
		this.stop;
		pipe.close;
	}

	tty_ { arg string;
		if(pipe.notNil, { pipe.close });
		tty = string;
		pipe = Pipe("tee %".format(tty), "w");
	}

	print { arg msg;
		msg.do(queue.addFirst(_));
	}

	start {
		printLoop = Routine.run({
			loop {
				if(queue.isEmpty, {
					pipe.putChar($*);
					pipe.flush;
					slow.wait;
				}, {
					while({ queue.notEmpty }, {
						pipe.putChar(queue.pop);
						pipe.flush;
						fast.wait;
					});
				});
			}
		}, clock: TempoClock(1, 0));
	}

	stop {
		printLoop.stop;
		printLoop = nil;
	}
}

WordPlayer {
	//var <>lifeTime = 60;
	var <ttm;
	var <soundsPool;
	var <soundsList;
	var <server;

	var playLoop;
	var <beats = 0;
	// agreagar limitador de palabras simultáneas

	*new { arg ttm, server;
		^super.new.init(ttm, server);
	}

	init { arg t, s;
		ttm = t;
		server = s;
		soundsList = List.new;
		WordSound.buildDefs;
	}

	start {
		if(playLoop.notNil, { ^this });
		playLoop = Routine.run({
			// init just in case
			soundsList.do({ arg i, j;
				"WordPlayer init %".format(i.word.toUpper).warn;
				i.play(rrand(5, 30))
			});

			loop {
				beats = thisThread.beats;

				soundsList.do({ arg i, j;
					// play new
					if(i.isPlaying.not, {
						"WordPlayer start %".format(i.word.toUpper).warn;
						i.play(rrand(5, 15))
					});

					// life time
					if(beats - i.additionTime > i.lifeTime, {
						soundsList.removeAt(j);
						"WordPlayer release %".format(i.word.toUpper).warn;
						i.release(rrand(5, 15));
					})
				});
				1.wait;
			}
		}, clock: TempoClock(1, 0));
	}

	stop {
		playLoop.stop;
		playLoop = nil;
	}

	pushSound { arg soundData, lifeTime = 30, amp = 0.125, play = false;
		soundsList.addFirst(
			WordSound(
				server, ttm.path +/+ ttm.soundsFolder,
				soundData, beats, play).lifeTime_(lifeTime).amp_(amp);
		);
	}

	popSound { arg sound, fadeOut = 1; // no pun intended...
		var wsound = soundsList.pop;
		wsound.release(fadeOut);
	}
}

WordSound {
	var <word;
	var <id;
	var <fileName;
	var <date;
	var <folder;
	var <additionTime;

	var <server;
	var <defName;
	var <synth;
	var <out = 0;
	var <buffer;

	var <>lifeTime = 60;
	var <>amp = 0.125;

	var buffReady = false;

	*new { arg server, folder, soundData, additionTime = 0, play = false;
		^super.new.init(server, folder, soundData, additionTime, play);
	}

	init { arg s, f, d, a, p;
		server = s;
		folder = f;
		word = d[0].asString;
		id = d[1];
		fileName = d[2];
		date = d[3];
		additionTime = a;
		buffer = Buffer.read(
			server,
			folder +/+ fileName,
			action: { arg b;
				defName = "wordSynth" ++ b.numChannels;
				b.normalize; // anda?
				buffReady = true;
				if(p, { this.play });
			}
		);
	}

	play { arg fadeIn = 0.02;
		if(buffReady.not, {
			fork {
				"*Buffer not ready test*".warn;
				1.wait;
				this.play(fadeIn);
			};
			^this
		});

		"% play %".format(this, buffer).warn;
		"numChannels %".format(buffer.numChannels).warn;

		if(synth.isNil, {
			synth = Synth(defName, [out: out, buf: buffer, amp: amp, fadeIn: fadeIn]);
		}, {
			synth.run(1);
		});
	}

	isPlaying {
		if(synth.notNil, { ^true }, { ^false });
	}

	pause {
		synth.run(0); // click
	}

	release { arg fadeOut = 0.02;
		synth.set(\fadeOut, fadeOut, \gate, 0);
		synth = nil;
		fork { fadeOut.wait; buffer.free; };
	}

	gui {}

	*buildDefs {
		[1, 2].do({ arg n;
			SynthDef("wordSynth" ++ n, {
				arg out, buf, amp = 0.2, gate = 1,
				fadeIn = 0.02, fadeOut = 0.02;

				var src, src2, env, snd;

				src = 4.collect({
					PlayBuf.ar(
						n, buf, BufRateScale.kr(buf),
						startPos: Rand(0, BufFrames.kr(buf)), loop: 1
					) * 0.25;
				});
				if(n == 2, { src2 = src.sum * 0.5 }, { src2 = src });

				src2 = GrainIn.ar(
					2, Dust.kr(LFNoise1.kr(0.5).range(2, 20)),
					LFNoise2.kr(0.5).range(0.01, 1),
					src2, LFNoise2.kr(1), mul: 0.1
				);

				env = EnvGen.kr(Env.asr(fadeIn, 1, fadeOut), gate, doneAction: 2);
				snd = src + src2 * env * amp;

				Out.ar(out, snd);
			}).add;
		});
	}
}

TTMDB {
	var <data;
	var <path;
	var <fileName;
	var <cpubind;
	var numaCmd = "";

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

	write { // fix: siempre vuelve a escribir todo, mutex?
		var dataFile;

		dataFile = File(path +/+ fileName, "w");
		dataFile.write(data.asCompileString);
		dataFile.close;
	}

	cpubind_ { arg cpus = "+0"; // +0-3,n-m
		numaCmd = "numactl --physcpubind=%".format(cpus);
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
			query: word, filter: "type:wav duration:[10 TO 120]", sort: "duration_asc",
			params: (page: 2), action: { arg fspager;
				if(fspager.dict.includesKey("results").not or:
					{ fspager.results.size <= index } or:
					{ fspager.results.size < 1 }, {
						"%: no se encontraron resultados para % index %"
						.format(this, word.toUpper, index).warn;
						action.value(nil);
					}, {
						var fssound = fspager.at(index);
						var filePath = path +/+ soundsFolder +/+ fssound.previewFilename;

						if(data.flat.includes(fssound.id.asSymbol).not, {
							fssound.retrievePreview(
								path +/+ soundsFolder,
								action: {
									if(File.exists(filePath), {
										retrieved = [
											word.asSymbol,
											fssound.id.asSymbol,
											fssound.previewFilename,
											Date.getDate.asSortableString
										];
										data = data.add(retrieved);
										this.write;
										action.value(retrieved);
									}, {
										"%: error en la descarga para %"
										.format(this, word.toUpper).warn;
									});
								},
								quality: "hq",
								format: "ogg"
							);
						}, {
							"%: el archivo ya fue descargado".format(this).warn;

							"DEVUELVE %".format(
								data.at(data.flat.indexOf(fssound.id.asSymbol) div: 4)
							).postln;

							action.value(
								data.at(data.flat.indexOf(fssound.id.asSymbol) div: 4)
							);
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
				if(append.isEmpty, {
					action.value(nil)
				}, {
					append = append.collect({ arg i; i[3] = i[3].replace($\n, " "); i });
					data = data ++ append; //.at(3); ???
					this.write;
					action.value(append)
				});
			});
		});
	}
}

Dictesen : TTMDB {
	var apertiumTaskManager2000; // ok, nothing seems to work
	var <>rwps = 0.1; // 10 wps
	var wordsList;

	*new { arg path, file = "dictesen.db";
		^super.new.init(path, file)
	}

	initData {
		data = data ? Dictionary.new;
		wordsList = List.new;
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
								this.write;
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

	start {
		if(apertiumTaskManager2000.notNil, { ^this });
		apertiumTaskManager2000 = Routine.run({
			loop {
				wordsList.pop !? { arg call; this.translateCall(*call) };
				rwps.wait;
			}
		}, clock: TempoClock.new);
	}

	stop {
		apertiumTaskManager2000.stop;
		apertiumTaskManager2000 = nil;
	}

	translate { arg palabra, direction = "es-en", action;
		wordsList.addFirst([palabra, direction, action]);
	}

	translateCall { arg palabra, direction = "es-en", action;
		var tmpFile = PathName.tmp +/+ "dictesen%".format(UniqueID.next);

		"*** *** ** * TRANSLATE * ** *** ***".postln;
		//"echo \"%\" | apertium % > %".format(palabra, direction, tmpFile)
		"sleep %; % echo \"%\" | % apertium % > %"
		.format(2.0.rand, numaCmd, palabra, numaCmd + "nice", direction, tmpFile)
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

		"*** *** ** * CHECK VALID WORD * ** *** ***".postln;
		//"echo \"%\" | lt-proc -a % > %".format(word, dict, tmpFile)
		"sleep %; % echo \"%\" | % lt-proc -a % > %"
		.format(2.0.rand, numaCmd, word, numaCmd + "nice", dict, tmpFile)
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

+ FSReq {
	init{|anUrl, params, method="GET"|
		var paramsString, separator = "?";
		url = anUrl;
		filePath = PathName.tmp ++ "fs_" ++ UniqueID.next ++ ".txt";
		params = params?IdentityDictionary.new;
		paramsString = params.keys(Array).collect({|k|
			k.asString ++ "=" ++ params[k].asString.urlEncode}).join("&");
		if (url.contains(separator)){separator = "&"};
		cmd = "curl -H '%' '%'>% ".format(
			FSReq.getHeader, this.url ++ separator ++ paramsString, filePath
		);
		//cmd.postln;
	}

	get{|action, objClass|
		cmd.unixCmd({|res,pid|
			var result = objClass.new(
				File(filePath,"r").readAllString; //.postln;
				Freesound.parseFunc.value(
					File(filePath,"r").readAllString
				)
			);
			action.value(result);
		});
	}

	*retrieve{|uri,path,action|
		var cmd;
		cmd = "curl -H '%' '%'>'%'".format(FSReq.getHeader, uri, path);
		//cmd.postln;
		cmd.unixCmd(action);
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
