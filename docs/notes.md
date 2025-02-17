# General notes


immediate todo

make a search box in the random pattern that limits the random patterns that are shown

get rid of the bug that shows an empty tag in the pattern details page

in addition to search, or maybe instead of search there should be a way for the coach to query the 
   db so that it can easily find out things like which 3 ball pattern that i have the most catches with
   without it needing to actually look at all the 3b patterns.

Jugcoach
-we need to make all tools in the testtool screen
-make some coach chat bubbles automatically collapsed
-fix the formatting in the coach chats
-If i can get it to make a notation with relative body catches and throws or spatial notation or whatever, then it could gradually get more and more specific as I get good at certain locations. This spatial thing would also make some many more records trackable.
-maybe it's just a 3d grid where numbers are 1,1,1 in the upper left front corner and digits just move up as they head to the lower back right. We can work up to detail by only thinking about extreme positions at first and working up to it.
-i just did 100 catches of the 3b cascade with the left hand over 1 space to the left, as will as the right sided version of this. So this could become it's own pattern that stems from cascade
-variations on patterns or artistic variations on patterns, like a cascade with breathing height change should be tracable as well, but maybe some indication in the pattern name that they specifically come from a different pattern.
-some coaches could use a slow-mo tool for analyzing exactly what i do on one side and then help me to reproduce it on the other side
-some freestyle coach could incorporate the things I've been working on with a more drill oriented coach
-all patterns can be blind, should these each be new patterns or some sort of modifier system or duplicate the whole project
-when I set a new record of any kind it should be really recorded in a master list
-I should re-record videos whenever possible, it would be nice to automate this process with a coach that can pull videos out for me.

we should make a way to make new coaches(Head Coach should be the default)
we need to try uploading the json of patterns into the app
the home screen should default to coach interaction
test chatting functionality
make a way for chatting to happen directly into headphones
hook up watch to have automatic responses that are generated based on what the coach has said so that i can just tap start/stop when it suggests i do a certain pattern

I should be able to scrape my data from skilldex and somehow use it to bootstrap an ai juggling trainer that encourages me to try new kinds of patterns.
-I need a convenient way to input exactly what i do, eventually this could just be done with it watching me in a video.
-an issue i am having with my current setup is that i am unmotivated to do a given pattern if I have a super high record in it.
-it could use watch data and camera to watch me and in situations where camera isn't convenient it could only have me work on patterns where it is confident that it can count my catches just with the watch data
-before doing any video stuff maybe i should get a setup where it is all done with text and timers
-look into the best ways to use llm to analyze video or skeletal tracking with ball tracking
-multiple coaches that each see the world very differently. They can copy existing databases and notes but then organize them in different ways, have very different system prompts, have different attitudes, etc.
-tags can have explanations, but they can also have notes and ideas associated to them. For instance 'symmetric' tag could have a note on it that any pattern that is symmetric can have closely related asymmetric patterns. Or any 'tennis' tag pattern can have closely related every 4th or everyth 5th or every 6th patterns, etc..
-it would be nice to eventually have the coach always having a stream of the practice so that it can record and make new patterns out of anything I do without me needing to do it all manually. Another example of this is something like the pattern '42333 with every 2nd 3 bumbled', a natural variation of this would be '42333 with every 1st(or 3rd) 3 bumbled'. It would also follow that 4233333 would be similar as well with each of those 3s bumbled or even multiples of them bumbled.
-it would be nice to have some coaches that aren't so into patterns and are more encouraging about freestyle or free flow stuff. They could watch me freestyle and track what sorts of things I'm doing and encourage me or remind me to do very different things in the freestyle. Eventually a coach could have multiple cameras. One that watches me and one that watches audience to see what things people like and what things they dont.don't.
-I can tell it how a specific pattern feels to me, like that mills mess pattern with takeouts over the top or takeouts through the middle
-I could have entire coaches that don't care about records or patterns, they just focus on encouraging freestyle variety. I can watch myself freestyle and just think of various visual aspects that change. Things like connected balls(yoyo), stretching arms height and other directions, becoming small either in pattern or in body, speed, orbiting, confusing, very simple..
-if i integrate jugcoach into skilldex then it will be much easier to letanyone use it, but then i will risk messing up skilldex. But I could just make it be a separate website that is linked to from skilldex with an export option of a user's records.
-every time i start a session with a coach they should backup the entire database so that no matter what they do we can see the dif and can always revert. Maybe those backups should be saved as well.
-there can be a list of unexplored ideas that coaches could randomly select from when they want to do something new and they could also have their own list of explored ideas that they have personal notes on.
-coaches could have meetings with each other as well, there could be meets of a bunch of them, or just two together that are working on similar things. There could be message boards or group chats or something that some coaches pay more attention to than others so that some coaches have more continuity between practice sessions than others.
-it would be really cool to have coaches eventually watch me juggle and then tell me how to adjust patterns while I'm doing them to settle into a different pattern that it tracks and records records from.
-coaches could eventually go through entire skilldex database and make more connections between patterns and tag things that are not yet tagged.

JugCoach
-would be nice to have a description of each throw for each pattern. Ideally this could be automated or partially automated
-a report that tells me what records I broke each day (once we get a convenient way to add runs with app)
-a no-llm coach would be good. It could even be a type of coach that is just governed by db lookups and random choices. There can be different weighting for different no-llm coaches

Juggling coach
-A general list of definitions would be good that they can look up
-maybe eventually fine-tuning
-look into hooking up a simpler tool using llm for initial message, maybe it sends a request over for a stronger llm
-they should often be able to create new prereqs for tennis patterns because we can just do "every fifths"