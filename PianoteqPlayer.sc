
PianoteqPlayer {
	var <routine;

	var <midinotes;
	var <durs;
	var <amps;
	var <sustains;
	var <repeats;
	var <>clock;
	var tempo;

	var <>aleaTransposition;
	var <>aleaAmplitudes;
	var <>aleaSustain;
	var <>midiout;

	var maxNote, minNote; // no lo estoy usando

	var <pianoteqCmd;
	var <defaultPreset;
	var <currentValue;

	*new { arg midinotes, durs, repeats = 1, midiout;
		^super.new.init(midinotes, durs, repeats, midiout);
	}

	*newMIDI { arg stream, repeats = 1, midiout;
		^super.new.initMIDI(stream, repeats, midiout);
	}

	// no funciona por ahora
	init { arg m, d, r, o;
		midinotes = m;
		durs = d;
		repeats = r;
		midiout = o;
		clock = TempoClock();
	}

	initMIDI { arg stream, r, o;
		var events = stream.all(());
		events.do({ arg e;
			midinotes = midinotes.add(e.midinote);
			durs = durs.add(e.dur);
			amps = amps.add(e.amp);
			sustains = sustains.add(e.sustain);
		});
		repeats = r;
		midiout = o;
		clock = TempoClock(queueSize: 4096); // tempo tiene que ser 1, midi calcula la duración fija
		tempo = 1;
		this.initPianoteqCmds;
		this.calcRange;
	}

	initPianoteqCmds {
		// name -> controller number
		pianoteqCmd = IdentityDictionary[
			// default full-featured preset
			\volume -> (cmd: 7, default: 127),
			\qFactor-> (cmd: 12, default: 64),
			\stringLength -> (cmd: 13, default: 60), // ~
			\sympatheticResonance -> (cmd: 15, default: 64),
			\dumplexScaleResonance -> (cmd: 17, default: 64),
			\quadraticEffect -> (cmd: 18, default: 64), // ?
			\mute -> (cmd: 19, default: 0),
			\sustainPedal -> (cmd: 64, default: 0),
			\sostenutoPedal -> (cmd: 66, default: 0),
			\softPedal -> (cmd: 67, default: 0),
			\harmonicPedal -> (cmd: 69, default: 0),
			\cutoff -> (cmd: 70, default: 64),
			\impedance -> (cmd: 72, default: 64),
			\directSoundDuration -> (cmd: 73, default: 64),
			\hammerHardPiano -> (cmd: 74, default: 25), // ~
			\hammerHardMezzo -> (cmd: 75, default: 60), // ~
			\hammerHardForte -> (cmd: 76, default: 110), // ~
			\hammerNoise -> (cmd: 77, default: 40), // ~
			\character -> (cmd: 78, default: 64), // ?
			\octaveStretching -> (cmd: 79, default: 32), // ~
			\reverbDuration -> (cmd: 80, default: 32), // ~
			\dynamics -> (cmd: 85, default: 60), // ~
			\soundSpeed -> (cmd: 86, default: 64), // ?
			\unisonWidth -> (cmd: 93, default: 64), // ~
			\softLevel -> (cmd: 94, default: 64), // ?
			// full-featured-plus preset
			\spectrumProfile1 -> (cmd: 40, default: 64),
			\spectrumProfile2 -> (cmd: 41, default: 64),
			\spectrumProfile3 -> (cmd: 42, default: 64),
			\spectrumProfile4 -> (cmd: 43, default: 64),
			\spectrumProfile5 -> (cmd: 44, default: 64),
			\spectrumProfile6 -> (cmd: 45, default: 64),
			\spectrumProfile7 -> (cmd: 46, default: 64),
			\spectrumProfile8 -> (cmd: 47, default: 64),
			\strikePoint -> (cmd: 50,  default: 70), // ~
			\bloomingEnergy -> (cmd: 51, default: 0),
			\bloomingInertia -> (cmd: 52, default: 64),
			\damperPosition -> (cmd: 53, default: 90), // ~
			\dampingDuration -> (cmd: 54, default: 70), // ~
			\keyReleaseNoise -> (cmd: 56, default: 64),
			\pedalNoise -> (cmd: 57, default: 60), // ~
			\lastDamper -> (cmd: 58, default: 90), // ~
			\reloadCurrentPreset -> (cmd: 59, default: 127) // RESET
		];

		pianoteqCmd.do({ arg i; i.val = i.default });
	}

	calcRange {
		maxNote = midinotes[midinotes.maxIndex];
		minNote = midinotes[midinotes.minIndex];
	}

	tempo_ { arg tempo;
		clock.tempo = tempo;
	}

	play { arg clock, condition;
		routine = this.buildRoutine(midinotes, durs, repeats, condition);
		this.clock = clock ? this.clock;
		routine.play(this.clock);
	}

	stop {
		routine.stop;
		midiout.allNotesOff(0); // siempre el primer canal
	}

	resetToPreset { arg time;
		if(time.isNil || time.round.asInteger <= 0, {
			this.setControl(\reloadCurrentPreset, 127);
			^this;
		});

		time = time.round;
		pianoteqCmd.keys.do({ arg i;
			fork {
				var key = i;
				var diff = pianoteqCmd[key].val - pianoteqCmd[key].default;
				var numSteps = time;
				var step = diff / numSteps;

				if(diff != 0, {
					numSteps.do({
						this.setControl(key, pianoteqCmd[key].val - step);
						1.wait;
					});
				});
			}
		});

		// safe, pero no se pueden hacer cambios antes de tiempo, idem antes
		SystemClock.sched(time, {
			this.setControl(\reloadCurrentPreset, 127);
			this.tempo = 1;
		});
	}

	buildRoutine { arg midinotes, durs, repeats = 1, condition;
		^Routine.new({
			repeats.do({
				[midinotes, durs, amps, sustains].flop.do({ arg i, j;
					var note = i[0];
					var dura = i[1];
					var ampl = i[2]; // if nil
					var sust = i[3]; // if nil

					(
						type: \midi,
						midiout: midiout,
						midinote: this.prTranspose(note),
						amp: this.prAmplitude(ampl),
						sustain: this.prSustain(sust),
					).play;

					dura.wait;
				});
			});

			if(condition.notNil, {
				condition.test = true;
				condition.signal;
			});
		});
	}

	prTranspose { arg note;
		// también se pueden multiplicar las notas al estilo Boulez
		if(aleaTransposition.isNil, { ^note });
		^note % 12 + aleaTransposition.asArray.choose;
	}

	prAmplitude { arg amp;
		if(aleaAmplitudes.isNil, { ^amp });
		^aleaAmplitudes.choose; // o mul como proporcional?
	}

	prSustain { arg sus;
		if(aleaSustain.isNil, { ^sus });
		^aleaSustain.choose;
	}

	setControl { arg name, value;
		//m.control(1, pianoteqCmd[name], value); // ch1
		pianoteqCmd[name].val = value;
		(
			type: \midi,
			midiout: midiout,
			midicmd: \control,
			ctlNum: pianoteqCmd[name].cmd,
			control: value
		).play;
	}
}
