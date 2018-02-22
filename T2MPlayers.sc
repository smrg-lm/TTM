
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

	// TASTE PARA ANEW
	pushSound { arg soundData, taste, lifeTime = 30, amp = 0.125, play = false;
		soundsList.addFirst(
			WordSound(
				server, t2m.path +/+ t2m.soundsFolder,
				soundData, taste, beats, play).lifeTime_(lifeTime).amp_(amp);
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

	// TASTE PARA ANEW
	var <taste;
	var <group;
	var <internalBus;
	var <synth2;

	// TASTE PARA ANEW
	*new { arg server, folder, soundData, taste, additionTime = 0, play = false;
		^super.new.init(server, folder, soundData, taste, additionTime, play);
	}

	// TASTE PARA ANEW
	init { arg s, f, d, t, a, p;
		server = s;
		folder = f;
		word = d[0].asString;
		id = d[1];
		fileName = d[2];
		date = d[3];
		taste = t.asString; // TASTE PARA ANEW
		additionTime = a;
		group = Group.new(server); // TASTE PARA ANEW
		internalBus = Bus.audio(server, 2); // TASTE PARA ANEW
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
			// TASTE PARA ANEW
			if(taste == nil.asString, { this.amp = this.amp * (1/16); });
			synth = Synth(defName, [out: internalBus, buf: buffer, amp: amp, fadeIn: fadeIn], this.group);
			synth2 = Synth(taste, [in: internalBus, out: out], this.group, 'addToTail'); // TASTE PARA ANEW
		}, {
			synth.run(1);
			synth2.run(1); // TASTE PARA ANEW
		});
	}

	isPlaying {
		if(synth.notNil, { ^true }, { ^false });
	}

	pause {
		synth.run(0); // click
		synth2.run(0); // TASTE PARA ANEW
	}

	release { arg fadeOut = 0.02;
		synth.set(\fadeOut, fadeOut, \gate, 0);
		synth = nil;
		fork {
			fadeOut.wait;
			buffer.free;
			internalBus.free; // TASTE PARA ANEW
			group.free; // synth2 free
		};
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

		// TASTE PARA ANEW
		SynthDef(\bitter, { arg in, out;
			var sig;

			sig = In.ar(in, 2);
			sig = PitchShift.ar(sig, 0.2, LFNoise2.kr(0.2).range(0.01, 0.2), [0.05, 0.1]);

			Out.ar(out, sig);
		}).add;

		SynthDef(\acid, { arg in, out;
			var sig;

			sig = In.ar(in, 2);
			sig = PitchShift.ar(sig, 0.2, LFNoise2.kr(0.8).range(2, 4), [0.5, 1]);

			Out.ar(out, sig);
		}).add;

		SynthDef(\salty, { arg in, out;
			var sig;

			sig = In.ar(in, 2);
			sig = sig * EnvGen.kr(Env.perc(0.01, 0.5), Impulse.kr(LFNoise2.kr(2!2).range(1, 3)));
			sig = CombC.ar(sig, 0.01, 0.019, mul: 0.7);
			Out.ar(out, sig);
		}).add;

		SynthDef(\sweet, { arg in, out;
			var sig;

			sig = In.ar(in, 2);
			sig = sig * EnvGen.kr(Env.sine(2), Impulse.kr(LFNoise2.kr(0.1!2).range(0.25, 1)));
			sig = CombC.ar(sig, 0.01, 0.007, mul: 0.5);

			Out.ar(out, sig);
		}).add;

		SynthDef(\nil, { arg in, out;
			Out.ar(out, In.ar(in, 2));
		}).add;
	}
}
