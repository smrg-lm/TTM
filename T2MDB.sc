
T2MDB {
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

Sounds : T2MDB {
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

Tweets : T2MDB {
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
