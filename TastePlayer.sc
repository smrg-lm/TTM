// los valores bastante son aproximados
// dependiendo de la amplitud parece que todo tiende a lo brillante

TastePlayer {
	var punct;

	var <>pianoteqPlayer;
	var <twitterTaste;

	var tweetsList;
	var routine;

	var <view;
	var <viewLabel;

	var <intensity;
	var <wineColor;
	var <nose;
	var <palate;
	var <tannins;

	var <>texturesPath; // "~/Desktop/Barcelona/Textures".standardizePath;
	var <>texturesAmp = 0.125;
	var playTanninsTexture = false;
	var <>transformDur = 20;
	var <>resetTransformDur = 7;

	*new { arg pianoteqPlayer, twitterTaste;
		^super.new.init(pianoteqPlayer, twitterTaste);
	}

	init { arg pp, tt;
		pianoteqPlayer = pp;
		twitterTaste = tt;
		this.initView;
		this.initDictionaries();

		punct = [ // very basic, because unicode lack, from T2M
			".", ",", ";", ":", "'", "`", "\"",
			"(", ")", "[", "]", "<", ">", "{", "}",
			"!", "¡", "?", "¿", "\\", "/", "|",
			"*", "#", "@", "$", // "-", "+", se usan
			"\n", "\t", "\f", "http", "https" // ...
		];

		Server.default.waitForBoot({
			TastePlayer.buildDefs;
		});
	}

	enqueueTweet { arg arr;
		tweetsList = tweetsList.add(arr);
	}

	play {
		//view.front;
		if(routine.notNil, { ^this });
		routine = this.buildRoutine;
		routine.play;
	}

	stop {
		routine.stop;
		routine = nil;
	}

	buildRoutine {
		^Routine({
			loop {
				//var list;

				defer {
					viewLabel.string = twitterTaste.tag; // can change
					view.refresh;
				};

				//list = tweetsList; // la referencia no funciona
				//tweetsList = nil;

				//list.do({ arg tweet, index;
				tweetsList.do({ arg tweet, index;
					this.resetTaste; // just in case the song changed with bad timing
					defer {
						viewLabel.string = "% | %".format(tweet[2], tweet[3]);
						view.refresh;
					};
					2.wait;

					this.processWords(this.extractWords(tweet.last));
					transformDur.wait;

					this.resetTransition(7);
					resetTransformDur.wait;

					tweetsList.removeAt(index); // esto funciona pero "está mal", y no encuentro una solución

					defer {
						viewLabel.string = twitterTaste.tag;
						view.refresh;
					};
					1.wait;
				});
				2.wait;
			}
		});
	}

	// *** visual ***

	initView {
		viewLabel = StaticText();
		//viewLabel.font = Font("DejaVu Sans Mono", 44); // FIX: CAMBIAR FONT
		viewLabel.stringColor = Color.white;
		viewLabel.background = Color.black;
		viewLabel.align = \center;

		view = View();
		view.name = "T2Piano: fullscreen para mostrar los tweets";
		view.layout = VLayout(viewLabel);
		view.layout.margins = 0!4;
		view.layout.spacing = 0;
		view.bounds = Rect(0, 0, 800, 600);
	}

	setViewFont { arg font;
		viewLabel.font = font;
	}

	// *** text ***

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
			this.setIntensity(catValue); // does nothing if nil
		});

		// color - no se pone la palabra color (o no importa, la ignora)
		catIndex = arr.find(["white"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // lemon-green, lemon, gold, amber, brown
			if(catValue.notNil && { catValue.contains("-") }, {
				catValue = catValue.split($-)[1] // lemon-green is green
			});
			this.setColor("white", catValue);
		});

		catIndex = arr.find(["rose"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // pink, salmon, orange, onion
			this.setColor("rose", catValue);
		});

		catIndex = arr.find(["red"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // purple, ruby, garnet, tawny, brown
			this.setColor("red", catValue);
		});

		// nose
		catIndex = arr.find(["nose"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // light, medium-, medium, medium+, pronounced
			if(catValue.notNil, {
				catValue = catValue.replace("-", "Light");
				catValue = catValue.replace("+", "Pronounced");
			});
			this.setNose(catValue);
		});

		// palate - no se pone la palabra palate
		catIndex = arr.find(["sweetness"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // dry, off-dry, medium-dry, medium-sweet, sweet, luscious
			if(catValue.notNil && { catValue.contains("-") }, {
				var words = catValue.split($-);
				catValue = words[0] ++ (words[1][0].toUpper ++ words[1][1..]);
			});
			this.setPalate("sweetness", catValue);
		});

		catIndex = arr.find(["sourness"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // low, medium-, medium, medium+, high
			if(catValue.notNil, {
				catValue = catValue.replace("-", "Low");
				catValue = catValue.replace("+", "High");
			});
			this.setPalate("sourness", catValue);
		});

		catIndex = arr.find(["body"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // light, medium-, medium, medium+, full
			if(catValue.notNil, {
				catValue = catValue.replace("-", "Light");
				catValue = catValue.replace("+", "Full");
			});
			this.setPalate("body", catValue);
		});

		catIndex = arr.find(["flavor"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // light, medium-, medium, medium+, pronounced
			if(catValue.notNil, {
				catValue = catValue.replace("-", "Light");
				catValue = catValue.replace("+", "Pronounced");
			});
			this.setPalate("flavor", catValue);
		});

		catIndex = arr.find(["texture"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // steely, oily, creamy
			this.setPalate("texture", catValue);
		});

		catIndex = arr.find(["finish"]);
		if(catIndex.notNil, {
			catValue = arr[catIndex+1]; // // short, medium- medium medium+ long
			if(catValue.notNil, {
				catValue = catValue.replace("-", "Short");
				catValue = catValue.replace("+", "Long");
			});
			this.setPalate("finish", catValue);
		});

		// tannins
		catIndex = arr.find(["tannins"]);
		if(catIndex.notNil, {
			var catValue1;

			catValue = arr[catIndex+1]; // level: low, medium-, medium, medium+, high
			if(catValue.notNil, {
				catValue = catValue.replace("-", "Low");
				catValue = catValue.replace("+", "High");
			});

			catValue1 = arr[catIndex+2]; // nature: coarse, fine-grained
			if(catValue1.notNil && { catValue1.contains("-") }, {
				var words = catValue1.split($-);
				catValue1 = words[0] ++ (words[1][0].toUpper ++ words[1][1..]);
			});

			this.setTannins(catValue, catValue1);
		});
	}

	// *** mapping ***

	initDictionaries {
		intensity = IdentityDictionary[
			\pale -> {
				// pocos componentes agudos
				//pianoteqPlayer.setControl(\spectrumProfile1, 64); // se usa en nose
				pianoteqPlayer.setControl(\spectrumProfile2, 0);
				pianoteqPlayer.setControl(\spectrumProfile3, 0);
				pianoteqPlayer.setControl(\spectrumProfile4, 0);
				pianoteqPlayer.setControl(\spectrumProfile5, 0);
				pianoteqPlayer.setControl(\spectrumProfile6, 0);
				pianoteqPlayer.setControl(\spectrumProfile7, 0);
				pianoteqPlayer.setControl(\spectrumProfile8, 0);
				pianoteqPlayer.setControl(\cutoff, 10);
				pianoteqPlayer.setControl(\softPedal, 127);
				pianoteqPlayer.setControl(\volume, 65); // SONORIDAD
				//pianoteqPlayer.setControl(\hammerHardPiano, 0); // se usa en sourness
				// baja intensidad (no se puede, se usa en flavor)
				// textura poco densa, baja simultaneidad
			},
			\medium -> {
				// "medios" componentes
				//pianoteqPlayer.setControl(\spectrumProfile1, 64); // se usa en nose
				pianoteqPlayer.setControl(\spectrumProfile2, 64);
				pianoteqPlayer.setControl(\spectrumProfile3, 64);
				pianoteqPlayer.setControl(\spectrumProfile4, 64);
				pianoteqPlayer.setControl(\spectrumProfile5, 64);
				pianoteqPlayer.setControl(\spectrumProfile6, 64);
				pianoteqPlayer.setControl(\spectrumProfile7, 64);
				pianoteqPlayer.setControl(\spectrumProfile8, 64);
				pianoteqPlayer.setControl(\cutoff, 96);
				pianoteqPlayer.setControl(\softPedal, 0);
				pianoteqPlayer.setControl(\volume, 110);
				//pianoteqPlayer.setControl(\hammerHardPiano, 40); // se usa en sourness
				// baja intensidad (no se puede, se usa en flavor)
				// textura medio densa, media simultaneidad
			},
			\deep -> {
				// "muchas" componentes
				//pianoteqPlayer.setControl(\spectrumProfile1, 64); // se usa en nose
				pianoteqPlayer.setControl(\spectrumProfile2, 70);
				pianoteqPlayer.setControl(\spectrumProfile3, 100);
				pianoteqPlayer.setControl(\spectrumProfile4, 127);
				pianoteqPlayer.setControl(\spectrumProfile5, 100);
				pianoteqPlayer.setControl(\spectrumProfile6, 95);
				pianoteqPlayer.setControl(\spectrumProfile7, 80);
				pianoteqPlayer.setControl(\spectrumProfile8, 70);
				pianoteqPlayer.setControl(\cutoff, 127);
				pianoteqPlayer.setControl(\softPedal, 0);
				pianoteqPlayer.setControl(\volume, 127);
				//pianoteqPlayer.setControl(\hammerHardPiano, 60);
				// baja intensidad (no se puede, se usa en flavor) // se usa en sourness
				// textura densa, muchas notas simultaneidad
			}
		];

		wineColor = IdentityDictionary[
			\white -> IdentityDictionary[
				\green -> {
					// modo mayor, tempo medio-rápido. Emoción: feliz y moderadamente potente.
					pianoteqPlayer.tempo = 2;
				}, // lemon-green
				\lemon -> {
					// similar a lemon-green
					pianoteqPlayer.tempo = 1.75;
				},
				\gold -> {
					// modo mayor, lento (baja densidad cronomètrica).
					// Emoción: algo débil y un poco más abajo en valencia que neutro (solemne?).
					pianoteqPlayer.tempo = 0.85;
				},
				\amber -> {
					// como Gold, un poco más fuerte y positivo emocionalmente
					pianoteqPlayer.tempo = 0.8;
				},
				\brown -> {
					//modo menor, tempo medio; Emoción:  valencia neutra, potencia emocional fuerte.
					pianoteqPlayer.tempo = 0.9;
				}
			],
			\rose -> IdentityDictionary[
				\pink -> {
					// Modo mayor, tempo lento. Emoción: algo débil, levemente positivo.
					pianoteqPlayer.tempo = 0.9;
				},
				\salmon -> {
					// Rápido, modo mayor. Emoción: potencia neutra, valencia positiva.
					pianoteqPlayer.tempo = 1.25;
				},
				\orange -> {
					// Modo Mayor tempo rápido. Emoción: medianamente potente y bastante positivo
					pianoteqPlayer.tempo = 1.5;
				},
				\onion -> {
					// Similar al orange.
					pianoteqPlayer.tempo = 1.4;
				} // onion skin
			],
			\red -> IdentityDictionary[
				\purple -> {
					// Parecido al lemon green, pero tempo más lento.
					// Emoción: Potencia mediana, valencia positiva alta
					pianoteqPlayer.tempo = 0.6;
				},
				\ruby -> {
					// Parecido a purple (no me dicen mucho estas categorías
					pianoteqPlayer.tempo = 0.55;
				},
				\garnet -> {
					// Similares, cada vez más potentes y menos positivos emocionalmente,
					// más pesados y lentos, quizás más apasionados, tendiendo al brown
					pianoteqPlayer.tempo = 0.5;
				},
				\tawny -> {
					// igual garnet...
					pianoteqPlayer.tempo = 0.45;
				},
				\brown -> {
					// como para el brown del vino blanco, pero más grave y lento, con timbres más ricos
					pianoteqPlayer.tempo = 0.4;
				}
			]
		];

		nose = IdentityDictionary[
			// timbres progresivamente más  ricos en componentes,
			// acordes más ricos y texturas más densas yendo de pale a deep.
			// Intensidad sonora creciente. // *** el problema es que acá normal es el máximo
			\light -> {
				pianoteqPlayer.setControl(\spectrumProfile1, 0);
				pianoteqPlayer.setControl(\spectrumProfile2, 22);
				pianoteqPlayer.setControl(\spectrumProfile3, 42);
				pianoteqPlayer.setControl(\spectrumProfile4, 52);
				pianoteqPlayer.setControl(\volume, 80); // SONORIDAD, se puede cambiar la ecualización?
			},
			\mediumLight -> {
				pianoteqPlayer.setControl(\spectrumProfile1, 20);
				pianoteqPlayer.setControl(\spectrumProfile2, 34);
				pianoteqPlayer.setControl(\spectrumProfile3, 44);
				pianoteqPlayer.setControl(\spectrumProfile4, 54);
				pianoteqPlayer.setControl(\volume, 90); // SONORIDAD
			},
			\medium -> {
				pianoteqPlayer.setControl(\spectrumProfile1, 42);
				pianoteqPlayer.setControl(\spectrumProfile2, 44);
				pianoteqPlayer.setControl(\spectrumProfile3, 54);
				pianoteqPlayer.setControl(\spectrumProfile4, 64);
				pianoteqPlayer.setControl(\volume, 100); // SONORIDAD
			},
			\mediumPronounced -> {
				pianoteqPlayer.setControl(\spectrumProfile1, 52);
				pianoteqPlayer.setControl(\spectrumProfile2, 64);
				pianoteqPlayer.setControl(\spectrumProfile3, 74);
				pianoteqPlayer.setControl(\spectrumProfile4, 84);
				pianoteqPlayer.setControl(\volume, 110); // SONORIDAD
			},
			\pronounced -> {
				pianoteqPlayer.setControl(\spectrumProfile1, 74);
				pianoteqPlayer.setControl(\spectrumProfile2, 78);
				pianoteqPlayer.setControl(\spectrumProfile3, 84);
				pianoteqPlayer.setControl(\spectrumProfile4, 88);
				pianoteqPlayer.setControl(\volume, 127); // SONORIDAD
			}
		];

		palate = IdentityDictionary[
			\sweetness -> IdentityDictionary[
				\dry -> {
					// Armonía disonante. Articulación: non legato.
					pianoteqPlayer.setControl(\impedance, 0);
					pianoteqPlayer.setControl(\damperPosition, 0);
					pianoteqPlayer.setControl(\unisonWidth, 127);
					pianoteqPlayer.setControl(\sympatheticResonance, 0);
					pianoteqPlayer.setControl(\dumplexScaleResonance, 0);
				},
				\offDry -> {
					// En las siguientes 3 categorías ir aumentando las
					// características del sweet: cada vez más legato y consonante.
					// (O bien simplemente agrupar off-dry, medium dry con dry y
					// medium-sweet, luscious con sweet)
					pianoteqPlayer.setControl(\impedance, 25);
					pianoteqPlayer.setControl(\damperPosition, 25);
					pianoteqPlayer.setControl(\unisonWidth, 110);
					pianoteqPlayer.setControl(\sympatheticResonance, 20);
					pianoteqPlayer.setControl(\dumplexScaleResonance, 20);
				},
				\mediumDry -> {
					pianoteqPlayer.setControl(\impedance, 50);
					pianoteqPlayer.setControl(\damperPosition, 50);
					pianoteqPlayer.setControl(\unisonWidth, 95);
					pianoteqPlayer.setControl(\sympatheticResonance, 40);
					pianoteqPlayer.setControl(\dumplexScaleResonance, 40);
				},
				\mediumSweet -> {
					pianoteqPlayer.setControl(\impedance, 75);
					pianoteqPlayer.setControl(\damperPosition, 75);
					pianoteqPlayer.setControl(\unisonWidth, 80);
					pianoteqPlayer.setControl(\sympatheticResonance, 50);
					pianoteqPlayer.setControl(\dumplexScaleResonance, 50);
					//pianoteqPlayer.aleaTransposition = [60, 84, 96]; // no
				},
				\sweet -> {
					// Armonía: consonante. Registro medio agudo/agudo.
					// Intensidad baja. Tempo/dens cronométrica: lento. Articulación: legato
					pianoteqPlayer.setControl(\impedance, 100);
					pianoteqPlayer.setControl(\damperPosition, 100);
					pianoteqPlayer.setControl(\unisonWidth, 70);
					pianoteqPlayer.setControl(\sympatheticResonance, 60);
					pianoteqPlayer.setControl(\dumplexScaleResonance, 90);
					pianoteqPlayer.tempo = 0.8;
					pianoteqPlayer.aleaTransposition = pianoteqPlayer.aleaTransposition ++ [60, 72, 84];
				},
				\luscious -> {
					// Extremar las características del dulce, podría agregarse alguna
					// textura sonora consonante que evolucione muy lentamente,
					// evocando el fluir de la miel
					pianoteqPlayer.setControl(\impedance, 127);
					pianoteqPlayer.setControl(\damperPosition, 127); // no tiene mucho efecto
					pianoteqPlayer.setControl(\unisonWidth, 60);
					pianoteqPlayer.setControl(\sympatheticResonance, 70);
					pianoteqPlayer.setControl(\dumplexScaleResonance, 100);
					pianoteqPlayer.tempo = 0.8;
					pianoteqPlayer.aleaTransposition = pianoteqPlayer.aleaTransposition ++ [60, 72, 84];
				}
			],
			\sourness -> IdentityDictionary[
				\low -> {
					// indefinido (ignorar la palabra low cuando está asociada a sourness).
					pianoteqPlayer.setControl(\octaveStretching, 42);
					pianoteqPlayer.setControl(\hammerHardPiano, 35);
					pianoteqPlayer.setControl(\hammerHardMezzo, 70);
					pianoteqPlayer.setControl(\hammerNoise, 50);
				},
				\mediumLow -> {
					pianoteqPlayer.setControl(\octaveStretching, 52);
					pianoteqPlayer.setControl(\hammerHardPiano, 45);
					pianoteqPlayer.setControl(\hammerHardMezzo, 80);
					pianoteqPlayer.setControl(\hammerNoise, 65);
				}, // indefinido
				\medium -> {
					pianoteqPlayer.setControl(\octaveStretching, 72);
					pianoteqPlayer.setControl(\hammerHardPiano, 65);
					pianoteqPlayer.setControl(\hammerHardMezzo, 90);
					pianoteqPlayer.setControl(\hammerNoise, 75);
				}, // indefinido
				\mediumHigh -> {
					// como en high pero menos extremo.
					pianoteqPlayer.setControl(\octaveStretching, 92);
					pianoteqPlayer.setControl(\hammerHardPiano, 85);
					pianoteqPlayer.setControl(\hammerHardMezzo, 100);
					pianoteqPlayer.setControl(\hammerNoise, 80);
					pianoteqPlayer.tempo = 1.5;
					pianoteqPlayer.aleaTransposition = pianoteqPlayer.aleaTransposition ++ [84, 96];
				},
				\high -> {
					// Pitch: extremo agudo. Tempo/dens cronométrica: muy rápido.
					// Intensidad: forte/ff. Armonía: disonante, rugosa.
					// Timbre: complejo y extremadamente brillante
					// *** no puede ser que todas las categorías cambien el timbre entre opaco y brillante
					pianoteqPlayer.setControl(\octaveStretching, 100);
					pianoteqPlayer.setControl(\hammerHardPiano, 100);
					pianoteqPlayer.setControl(\hammerHardMezzo, 100);
					pianoteqPlayer.setControl(\hammerNoise, 85);
					pianoteqPlayer.tempo = 1.5;
					pianoteqPlayer.aleaTransposition = pianoteqPlayer.aleaTransposition ++ [84, 85, 96];
				}
			],
			\body -> IdentityDictionary[
				\light -> { // ignorar
					pianoteqPlayer.setControl(\dampingDuration, 0);
					pianoteqPlayer.setControl(\lastDamper, 127);
				},
				\mediumLight -> { // ignorar
					pianoteqPlayer.setControl(\dampingDuration, 32);
					pianoteqPlayer.setControl(\lastDamper, 100);
				},
				\medium -> { // ignorar
					pianoteqPlayer.setControl(\dampingDuration, 64); // default
					pianoteqPlayer.setControl(\lastDamper, 91); // default
				},
				\mediumFull -> {
					// Ignorar las calificaciones hasta medium, para medium(+) – full:
					// sonido pleno,  densidad textural, espectros ricos.
					pianoteqPlayer.setControl(\dampingDuration, 96);
					pianoteqPlayer.setControl(\lastDamper, 80);
				},
				\full -> {
					pianoteqPlayer.setControl(\dampingDuration, 127);
					pianoteqPlayer.setControl(\lastDamper, 70); // default
				}
			],
			\flavor -> IdentityDictionary[ // flavor intensity
				// mapear a intensidad sonora
				\light -> {
					pianoteqPlayer.setControl(\volume, 65);
				},
				\mediumLight -> {
					pianoteqPlayer.setControl(\volume, 80);
				},
				\medium -> {
					pianoteqPlayer.setControl(\volume, 95);
				},
				\mediumPronounced -> {
					pianoteqPlayer.setControl(\volume, 110);
				},
				\pronounced -> {
					pianoteqPlayer.setControl(\volume, 127);
				}
			],
			\texture -> IdentityDictionary[
				// Agregar alguna textura sonora (tengo ejemplos de oily y creamy,
				// habría que conseguirse una lista más completa)
				\steely -> {
					this.readAndPlayTexture("steely", texturesAmp);
				},
				\oily -> {
					this.readAndPlayTexture("oily", texturesAmp);
				},
				\creamy -> {
					this.readAndPlayTexture("creamy", texturesAmp);
				}
			],
			\finish -> IdentityDictionary[
				// Esto es cuanto dura el sabor en boca, luego puede
				// relacionarse con la duración de la música c con la duración
				// de una resonancia que quede al final.
				\short -> {
					this.resetTransition(5);
				},
				\mediumShort -> {
					this.resetTransition(10);
				},
				\medium -> {
					this.resetTransition(15);
				},
				\mediumLong -> {
					this.resetTransition(20);
				},
				\long -> {
					this.resetTransition(25);
				},
			]
		];

		/*
		Ignorar los niveles más bajos. medium(+) – high: Sonido seco,
		discontinuo. Pitch: grave. Tempo/dens. cronometica: lento.
		Timbre: disonante, rugoso. Armonía disonante nature e.g.
		ripe/soft vs unripe/green/stalky, coarse vs fine-grained.
		Ignorar ripe/soft y unripe/green/stalky. Para coarse vs fine-grained
		se puede agregar una textura granular sugiriendo esas características.
		*/
		tannins = IdentityDictionary[
			\level -> IdentityDictionary[
				\low -> { playTanninsTexture = false },
				\mediumLow -> { playTanninsTexture = false },
				\medium -> { playTanninsTexture = false },
				\mediumHigh -> {
					playTanninsTexture = true;
					pianoteqPlayer.setControl(\dampingDuration, 0);
					pianoteqPlayer.aleaTransposition = pianoteqPlayer.aleaTransposition ++ [36, 48];
				},
				\high -> {
					playTanninsTexture = true;
					pianoteqPlayer.aleaTransposition = pianoteqPlayer.aleaTransposition ++ [36, 37, 48];
					pianoteqPlayer.setControl(\dampingDuration, 0);
				}
			],
			\nature -> IdentityDictionary[
				\coarse -> {
					// archivo con textura, es condicional a si los taninos son meidium+ y high
					if(playTanninsTexture, { this.readAndPlayTexture("coarse", texturesAmp) });
				},
				\fineGrained -> {
					// archivo con textura, es condicional a si los taninos son meidium+ y high
					if(playTanninsTexture, { this.readAndPlayTexture("finegrained", texturesAmp) });
				},
			]
		];
	}

	setIntensity { arg degree;
		/*
		degree:
		- pale
		- medium
		- deep
		*/
		intensity[degree.asSymbol].value;
		"setIntencity | degree: %".format(degree).debug;
	}

	setColor { arg color, tone;
		/*
		white:
		- lemon-green \green
		- lemmon
		- gold
		- amber
		- brown
		rosé:
		- pink
		- salmon
		- orange
		- onion skin
		red:
		- purple
		- ruby
		- garnet y tawny
		- brown
		*/
		wineColor[color.asSymbol][tone.asSymbol].value;
		"setColor | color: %, tone: %".format(color, tone).debug;
	}

	setNose { arg intensity;
		/*
		intensity:
		- light
		- medium(-) \mediumLight
		- medium
		- medium(+) \mediumPronounced
		- pronounced
		*/
		nose[intensity.asSymbol].value;
		"setNose | intensity: %".format(intensity).debug;
	}

	setPalate { arg feature, value;
		/*
		sweetness:
		- dry
		- off-dry
		- medium-dry
		- medium-sweet
		- sweet
		- luscious

		sourness:
		- low
		- medium(-)
		- medium
		- medium(+)
		- high

		body:
		- light // ignorar
		- medium(-) // ignorar
		- medium // ignorar
		- medium(+)
		- full

		flavor intensity:
		- light
		- medium(-)
		- medium
		- medium(+)
		- pronounced

		texture:
		- steely
		- oily
		- creamy

		finish:
		- no lo puso, debe ser short, medium long?
		*/
		palate[feature.asSymbol][value.asSymbol].value;
		"setPalate: feature: %, value: %".format(feature, value).debug;
	}

	setTannins { arg level, nature; // es sub categoría de palate pero tiene un campo más
		/*
		level: low, medium(-), medium, (ignora medium(+), high)
		nature: coarse vs fine-grained (o, ignora ripe/soft vs unripe/green/stalky)
		*/
		tannins[\level][level.asSymbol].value; // order matters, sets playTanninsTexture for nature
		tannins[\nature][nature.asSymbol].value;
		"setTannins | level: %, nature: %".format(level, nature).debug;
	}

	resetTaste {
		pianoteqPlayer.setControl(\reloadCurrentPreset, 127);
		pianoteqPlayer.aleaTransposition = nil;
	}

	resetTransition { arg time;
		pianoteqPlayer.resetToPreset(time);
	}

	// from WordSound
	*buildDefs {
		[1, 2].do({ arg n;
			SynthDef("textureSynth" ++ n, {
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

	// hack
	readAndPlayTexture { arg folderName, amp = 0.2;
		var filePath;

		if(texturesPath.isNil, { "Falta setear el path de las exturas correctamente!".warn });
		filePath = PathName(texturesPath +/+ folderName).files.choose;
		if(filePath.isNil, { ^this }, { filePath = filePath.fullPath });

		Buffer.read(
			Server.default, // hard
			filePath,
			action: { arg b;
				var defName = "textureSynth" ++ b.numChannels;
				b.normalize;
				fork {
					var synth;
					Server.default.sync; // hard
					synth = Synth(defName, [
						out: 0, buf: b, amp: amp,
						fadeOut: resetTransformDur
					]);
					transformDur.wait;
					synth.release;
					resetTransformDur.wait;
					b.free;
				}
			}
		);
	}
}
