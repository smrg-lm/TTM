
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
	var <t2m;
	var <soundsPool;
	var <soundsList;
	var <server;

	var playLoop;
	var <beats = 0;
	// agreagar limitador de palabras simultáneas

	*new { arg t2m, server;
		^super.new.init(t2m, server);
	}

	init { arg t, s;
		t2m = t;
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
				server, t2m.path +/+ t2m.soundsFolder,
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
