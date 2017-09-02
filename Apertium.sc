
Apertium {
	var cmd;
	var <>opt;
	var <pid;

	var url;
	var <langPairs;


	*new { arg port = "2737", j = 1;
		^super.new.init(port, j);
	}

	init { arg port, j;
		cmd = "/usr/bin/python3 /usr/share/apertium-apy/servlet.py -p % -j % /usr/share/apertium &";
		url = "http://localhost:%/%";
		opt = (port: port, j: j);
	}

	boot {
		pid = cmd.format(opt.port, opt.j).unixCmd; // x.X not the actual pid but a defunct sh
		//fork { while{ ... } { 0.2.wait }; };
	}

	quit {
		/*if(pid.pidRunning) {
			"kill -TERM %".format(pid).unixCmd;
		} {
			"Apertium server is not running.".inform;
		};*/
	}

	kill {
		/*if(pid.pidRunning) {
			"kill -KILL %".format(pid).unixCmd;
		} {
			"Apertium server is not running.".inform;
		};*/
	}

	detectLanguage { arg string, action;
		var str;
		str = url.format(opt.port, "identifyLang?q=%");
		str = str.format(string.replace(" ", "+"));
		this.prAsyncCall(str, action);
	}

	translate { arg string, langpair, action;
		var str;
		str = url.format(opt.port, "translate?langpair=%&q=%");
		str = str.format(langpair, string.replace(" ", "+"));
		this.prAsyncCall(str, action);
	}

	analize { arg string, lang, action;
		var str;
		str = url.format(opt.port, "analyze?lang=%&q=%");
		str = str.format(lang, string.replace(" ", "+"));
		this.prAsyncCall(str, action);
	}

	perWord { arg string, lang, modes = "morph", action;
		var str;
		str = url.format(opt.port, "perWord?lang=%&modes=%&q=%");
		str = str.format(lang, modes, string.replace(" ", "+"));
		this.prAsyncCall(str, action);
	}

	prAsyncCall { arg str, action;
		var tmpFile = PathName.tmp +/+ "apertium__%".format(UniqueID.next);
		str = "curl \"%\"".format(str);
		str = str + ">" + tmpFile;
		str.postln;
		str.unixCmd({ arg res, pid;
			var file, cmdOut;
			file = File.open(tmpFile, "r");
			cmdOut = file.readAllString;
			file.close;
			action.value(cmdOut);
		});
	}
}
