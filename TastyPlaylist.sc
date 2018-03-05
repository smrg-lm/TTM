// needs SimpleMIDIFile from wslib

TastyPlaylist {
	var routine;
	var clock;
	var path;
	var list;
	var midiout;

	var <pianoPlayer;
	var <tastePlayer;
	var <twitterTaste;

	var gain;
	var searchWaitTime;

	*new { arg path, list;
		^super.new.init(path, list);
	}

	init { arg p, l;
		path = p;
		list = l;
		if(MIDIClient.initialized.not, { MIDIClient.init });
		midiout = MIDIOut(0); // puerto de salida 0, la conexión se hace externamente
		twitterTaste = TwitterTaste("~/Desktop".standardizePath); // hard
		tastePlayer = TastePlayer(twitterTaste: twitterTaste);
		twitterTaste.tastePlayer = tastePlayer; // la composición está mal
	}

	play { arg twitterWaitTime = 10, conversionGain = 0.5; // para conversión
		if(routine.notNil, { ^this });
		gain = conversionGain;
		searchWaitTime = twitterWaitTime;
		routine = this.buildRoutine;
		routine.play;
	}

	stop {
		twitterTaste.stop;
		tastePlayer.stop;
		pianoPlayer.stop;
		routine.stop;
		routine = nil;
	}

	buildRoutine {
		^Routine({
			var songNum, filePath, midiFile;
			var midiPattern, condition;

			twitterTaste.start(waitTime: searchWaitTime); // esto podría estar siempre on

			list.scramble.do({ arg n;
				songNum = this.padIndex(n, 3); // hard
				filePath = path +/+ "%-Jazz.mid".format(songNum).standardizePath; // hard

				midiFile = SimpleMIDIFile.read(filePath);
				midiFile.info; // post
				midiPattern = midiFile.p(amp: gain);

				pianoPlayer = PianoteqPlayer.newMIDI(midiPattern.list[0].asStream, 1, midiout);
				condition = Condition();
				pianoPlayer.play(condition: condition);

				tastePlayer.pianoteqPlayer = pianoPlayer;
				tastePlayer.play;

				condition.wait;
				tastePlayer.stop;
				2.wait;
			});
		})
	}

	padIndex { arg i, size = 3;
		var str = i.asString;
		^(String.fill(size-str.size, { $0 }) ++ str);
	}
}
