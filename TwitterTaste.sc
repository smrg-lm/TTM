
TwitterTaste {
	var <tag;
	var <tweets;
	var <>tastePlayer;
	var routine;

	*new { arg path, tastePlayer;
		^super.new.init(path, tastePlayer);
	}

	init { arg path, tp;
		tweets = Tweets(path);
		tastePlayer = tp;
	}

	start { arg waitTime = 20;
		if(routine.notNil, { ^this });
		routine = this.searchRoutine(waitTime);
		routine.play;
	}

	stop {
		routine.stop;
		routine = nil;
	}

	buildQuery { arg n = 5, hashtag ...or;
		tag = hashtag;
		tweets.buildQuery(n, hashtag, *or);
	}

	searchRoutine { arg time;
		^Routine({
			loop {
				tweets.search({ arg list;
					if(list.notNil, {
						list.do({ arg i;
							tastePlayer.enqueueTweet(i);
						});
					});
				});
				time.wait;
			};
		});
	}
}
