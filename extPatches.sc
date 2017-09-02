
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
