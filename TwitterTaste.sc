
TwitterTaste {
	var <tweets;
	var <>tastePlayer;
	var routine;
	var punct;

	*new { arg path, tastePlayer;
		^super.new.init(path, tastePlayer);
	}

	init { arg path, tp;
		tweets = Tweets(path);
		tastePlayer = tp;

		punct = [ // very basic, because unicode lack, from T2M
			".", ",", ";", ":", "'", "`", "\"",
			"(", ")", "[", "]", "<", ">", "{", "}",
			"!", "¡", "?", "¿", "\\", "/", "|",
			"*", "#", "@", "$", // "-", "+", se usan
			"\n", "\t", "\f", "http", "https" // ...
		];
	}

	start { arg waitTime = 10;
		routine = this.searchRoutine(waitTime);
		routine.play;
	}

	stop {
		routine.stop;
		routine = nil;
	}

	buildQuery { arg n = 5, hashtag ...or;
		tweets.buildQuery(n, hashtag, *or);
	}

	searchRoutine { arg time;
		^Routine({
			loop {
				tweets.search({ arg list;
					if(list.notNil, {
						list.do({ arg i;
							this.processWords(this.extractWords(i.last));
						});
					});
				});
				time.wait;
			};
		});
	}

	// from T2M
	extractWords { arg string;
		var blanks;

		// filter all punct...
		punct.do({ arg i; string = string.replace(i, " "); });
		blanks = string.findRegexp(" {2,}");
		blanks.do({ arg i; string = string.replace(i[1], " ") });
		string = string.toLower;

		^string.split($ );
	}

	processWords { arg arr;
		var catIndex, catValue;

		// intensity
		catIndex = arr.find(["intensity"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // pale, medium, deep
			tastePlayer.setIntensity(catValue); // does nothing if nil
		});

		// color - no se pone la palabra color (o no importa, la ignora)
		catIndex = arr.find(["white"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // lemon-green, lemon, gold, amber, brown
			if(catValue.contains("-"), { catValue = catValue.split($-)[1] }); // lemon-green is green
			tastePlayer.setColor("white", catValue);
		});

		catIndex = arr.find(["rose"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // pink, salmon, orange, onion
			tastePlayer.setColor("rose", catValue);
		});

		catIndex = arr.find(["red"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // purple, ruby, garnet, twany, brown
			tastePlayer.setColor("red", catValue);
		});

		// nose
		catIndex = arr.find(["nose"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // light, medium-, medium, medium+, pronounced
			catValue = catValue.replace("-", "Light");
			catValue = catValue.replace("+", "Pronounced");
			tastePlayer.setNose(catValue);
		});

		// palate - no se pone la palabra palate
		catIndex = arr.find(["sweetness"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // dry, off-dry, medium-dry, medium-sweet, sweet, luscious
			if(catValue.contains("-"), {
				var words = catValue.split($-);
				catValue = words[0] ++ (words[1][0].toUpper ++ words[1][1..]);
			});
			tastePlayer.setPalate("sweetness", catValue);
		});

		catIndex = arr.find(["sourness"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // low, medium-, medium, medium+, high
			catValue = catValue.replace("-", "Low");
			catValue = catValue.replace("+", "High");
			tastePlayer.setPalate("sourness", catValue);
		});

		catIndex = arr.find(["body"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // light, medium-, medium, medium+, full
			catValue = catValue.replace("-", "Light");
			catValue = catValue.replace("+", "Full");
			tastePlayer.setPalate("body", catValue);
		});

		catIndex = arr.find(["flavor"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // light, medium-, medium, medium+, pronounced
			catValue = catValue.replace("-", "Light");
			catValue = catValue.replace("+", "Pronounced");
			tastePlayer.setPalate("flavor", catValue);
		});

		catIndex = arr.find(["texture"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // steely, olly, creamy
			tastePlayer.setPalate("texture", catValue);
		});

		catIndex = arr.find(["finish"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // // short, medium- medium medium+ long
			catValue = catValue.replace("-", "Short");
			catValue = catValue.replace("+", "Long");
			tastePlayer.setPalate("finish", catValue);
		});

		// tannins
		catIndex = arr.find(["tannins"]);
		if(catIndex.notNil, {
			var catValue1;

			catValue = arr[catIndex+1]; // level: low, medium-, medium, medium+, high
			catValue = catValue.replace("-", "Low");
			catValue = catValue.replace("+", "High");

			catValue1 = arr[catIndex+2]; // nature: coarse, fine-grained
			if(catValue1.contains("-"), {
				var words = catValue1.split($-);
				catValue1 = words[0] ++ (words[1][0].toUpper ++ words[1][1..]);
			});

			tastePlayer.setTannins(catValue, catValue1);
		});
	}
}
