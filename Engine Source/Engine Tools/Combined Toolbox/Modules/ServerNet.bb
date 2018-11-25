; Realm Crafter Server Net module by Rob W (rottbott@hotmail.com), August 2004

Type QueuedPacket
	Field Connection, Destination, PacketType, Pa$, ReliableFlag, PlayerFrom
	Field NextInQueue.QueuedPacket, PreviousInQueue.QueuedPacket
	Field PreviousSentTime
End Type

; Queues a packet (queued packets are delayed so that for each destination, only one is sent per 12 milliseconds)
Function SendQueued(Connection, Destination, PacketType, Pa$, ReliableFlag = False, PlayerFrom = 0)

	; Create packet
	Q.QueuedPacket = New QueuedPacket
	Q\Connection = Connection
	Q\Destination = Destination
	Q\PacketType = PacketType
	Q\Pa$ = Pa$
	Q\ReliableFlag = ReliableFlag
	Q\PlayerFrom = PlayerFrom
	Q\PreviousSentTime = MilliSecs() - 8

	; Attempt to find previous packet in queue
	For Q2.QueuedPacket = Each QueuedPacket
		If Q2\NextInQueue = Null And Q2\Destination = Destination And Q2\Connection = Connection
			If Q2 <> Q
				Q2\NextInQueue = Q
				Q\PreviousInQueue = Q2
				Exit
			EndIf
		EndIf
	Next

End Function

; Processes all network messages
Function UpdateNetwork()

	; Network data logging
	If LogNetwork > 0
		; Write to file
		If MilliSecs() - LogNetworkTime >= 5000
			Players = 0
			For AI.ActorInstance = Each ActorInstance
				If AI\RNID > 0 Then Players = Players + 1
			Next
			TrafficIn = (RN_BytesReceived(Host) - LogNetworkBytesIn) / 5
			TrafficOut = (RN_BytesSent(Host) - LogNetworkBytesOut) / 5
			L = StartLog("Network Data Dump")
				WriteLog(L, "Players in game: " + Players)
				WriteLog(L, "Average traffic in: " + TrafficIn + " bytes/second", False)
				WriteLog(L, "Average traffic out: " + TrafficOut + " bytes/second", False)
			StopLog(L)
			LogNetworkBytesIn = RN_BytesReceived(Host)
			LogNetworkBytesOut = RN_BytesSent(Host)
			LogNetwork = LogNetwork - 1
			LogNetworkTime = MilliSecs()
		EndIf
	EndIf

	; Send off any queued messages
	For Q.QueuedPacket = Each QueuedPacket
		If Q\PreviousInQueue = Null
			If MilliSecs() - Q\PreviousSentTime >= 12
				; Send it
				RN_Send(Q\Connection, Q\Destination, Q\PacketType, Q\Pa$, Q\ReliableFlag, Q\PlayerFrom)

				; Tell next in queue when this one was sent
				If Q\NextInQueue <> Null Then Q\NextInQueue\PreviousSentTime = MilliSecs()

				; Remove from queue
				Delete(Q)
			EndIf
		EndIf
	Next

	; Incoming messages
	For M.RN_Message = Each RN_Message
		Select M\MessageType

			; Chat message
			Case P_ChatMessage
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If Len(M\MessageData$) > 0 And AI <> Null
					; Command
					If Left$(M\MessageData$, 1) = "/" Or Left$(M\MessageData$, 1) = "\"
						Command$ = Mid$(M\MessageData$, 2)
						SpacePos = Instr(Command$, " ")
						If SpacePos > 0
							Params$ = Trim$(Mid$(Command$, SpacePos + 1))
							Command$ = Upper$(Left$(Command$, SpacePos - 1))
						Else
							Command$ = Upper$(Command$)
							Params$ = ""
						EndIf
						Select Command$
							Case "KICK"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									A2.ActorInstance = FindActorInstanceFromName(Params$)
									If A2 <> Null And A2\RNID > 0
										RN_KickPlayer(Host, AI\RNID)
									EndIf
								EndIf
							Case "UNIGNORE"
								A2.ActorInstance = FindActorInstanceFromName(Params$)
								If A2 <> Null And A2 <> AI
									If A2\RNID >= 0
										Pos = PlayerIgnoring(AI, A2)
										If Pos > 0
											Ac1.Account = Object.Account(AI\Account)
											EndPos = Instr(Ac1\Ignore$, ",", Pos)
											Ac1\Ignore$ = Left$(Ac1\Ignore$, Pos - 1) + Mid$(Ac1\Ignore$, EndPos + 1)
											RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_UnIgnoring) + " " + Params$, True)
										EndIf
									EndIf
								EndIf
							Case "IGNORE"
								A2.ActorInstance = FindActorInstanceFromName(Params$)
								If A2 <> Null And A2 <> AI
									If A2\RNID >= 0
										If PlayerIgnoring(AI, A2) = 0
											Ac1.Account = Object.Account(AI\Account)
											Ac2.Account = Object.Account(A2\Account)
											Ac1\Ignore$ = Ac1\Ignore$ + Ac2\User$ + ","
										EndIf
										RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_Ignoring) + " " + Params$, True)
									EndIf
								EndIf
							Case "NETDUMP"
								A.Account = Object.Account(AI\Account)
								If LogNetwork = False And A\IsDM = True
									RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Starting new net dump...", True)
									L = StartLog("Network Data Dump")
										WriteLog(L, "Starting new net dump...", True, True)
									StopLog(L)
									LogNetwork = 6
									LogNetworkTime = MilliSecs()
									LogNetworkBytesIn = RN_BytesReceived(Host)
									LogNetworkBytesOut = RN_BytesSent(Host)
								EndIf
							Case "PET"
								If AI\NumberOfSlaves > 0
									Name$ = Upper$(Trim$(Split$(Params$, 1, ",")))
									Command$ = Trim$(Split$(Params$, 2, ","))
									PetParams$ = Trim$(Split$(Params$, 3, ","))
									Found = 0
									For AI2.ActorInstance = Each ActorInstance
										If AI2\Leader = AI
											Found = Found + 1
											If Upper$(AI2\Name$) = Name$ Or Name$ = "ALL"
												CommandPet(AI2, Command$, PetParams$)
												If Name$ <> "ALL" Then Exit
											EndIf
											If Found = AI\NumberOfSlaves Then Exit
										EndIf
									Next
								EndIf
							Case "LEAVE"
								LeaveParty(AI)
							Case "OK"
								Party.Party = Object.Party(AI\AcceptPending)
								If Party <> Null
									; Check there's a free space in the party
									If Party\Members < 8
										; Remove from old party if required
										LeaveParty(AI)
										; Add to new party and tell players
										For i = 0 To 7
											If Party\Player[i] <> Null
												RN_Send(Host, Party\Player[i]\RNID, P_ChatMessage, Chr$(254) + AI\Name$ + " " + LanguageString$(LS_XHasJoinedParty), True)
											ElseIf AI\AcceptPending <> 0
												RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_YouHaveJoinedParty), True)
												AI\PartyID = Handle(Party)
												AI\AcceptPending = 0
												Party\Player[i] = AI
												Party\Members = Party\Members + 1
											EndIf
										Next
										For i = 0 To 7
											If Party\Player[i] <> Null Then SendPartyUpdate(Party\Player[i])
										Next
										; Run script
										RunScript("Party", "Join", AI, Null)
									; Party is full
									Else
										RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_CouldNotJoinParty), True)
										AI\AcceptPending = 0
									EndIf
								EndIf
							Case "INVITE"
								A2.ActorInstance = FindActorInstanceFromName(Params$)
								If A2 <> Null And A2 <> AI
									If A2\RNID > 0
										If PlayerIgnoring(A2, AI) = 0
											Party.Party = Object.Party(AI\PartyID)
											; Create new party if required
											If Party = Null
												Party = New Party
												AI\PartyID = Handle(Party)
												Party\Members = 1
												Party\Player[0] = AI
												RunScript("Party", "Join", AI, Null)
											EndIf
											; Check there's a free space in the party
											If Party\Members < 8
												A2\AcceptPending = Handle(Party)
												RN_Send(Host, A2\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PartyInvite) + " " + AI\Name$, True)
												RN_Send(Host, A2\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PartyInviteInstruction), True)
											; Party is full
											Else
												RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_CouldNotInviteParty), True)
											EndIf
										EndIf
									EndIf
								EndIf
							Case "XP"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True Then GiveXP(AI, Int(Params$))
							Case "GOLD", "MONEY"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Change = Int(Params$)
									AI\Gold = AI\Gold + Change
									If Change > 0
										Pa$ = "U" + RN_StrFromInt$(Change, 4)
									Else
										Pa$ = "D" + RN_StrFromInt$(Abs(Change), 4)
									EndIf
									RN_Send(Host, AI\RNID, P_GoldChange, Pa$, True)
								EndIf
							Case "SETATTRIBUTE"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Attribute = FindAttribute(Split$(Params$, 1, ","))
									If Attribute > -1
										If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
											UpdateAttribute(AI, Attribute, Int(Split$(Params$, 2, ",")))
										Else
											AI\Attributes\Value[Attribute] = Int(Split$(Params$, 2, ","))
											Pa$ = RN_StrFromInt$(AI\RuntimeID, 2) + RN_StrFromInt$(Attribute, 1) + RN_StrFromInt$(AI\Attributes\Value[Attribute], 2)
											RN_Send(Host, AI\RNID, P_StatUpdate, "A" + Pa$, True)
										EndIf
									EndIf
								EndIf
							Case "SETATTRIBUTEMAX"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Attribute = FindAttribute(Split$(Params$, 1, ","))
									If Attribute > -1
										If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
											UpdateAttributeMax(AI, Attribute, Int(Split$(Params$, 2, ",")))
										Else
											AI\Attributes\Maximum[Attribute] = Int(Split$(Params$, 2, ","))
											Pa$ = RN_StrFromInt$(AI\RuntimeID, 2) + RN_StrFromInt$(Attribute, 1) + RN_StrFromInt$(AI\Attributes\Maximum[Attribute], 2)
											RN_Send(Host, AI\RNID, P_StatUpdate, "M" + Pa$, True)
										EndIf
									EndIf
								EndIf
							Case "SCRIPT"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Name$ = Trim$(Split$(Params$, 1, ","))
									Func$ = Trim$(Split$(Params$, 2, ","))
									RunScript(Name$, Func$, AI, Null)
								EndIf
							Case "ME"
								Pa$ = Chr$(252) + "* " + AI\Name$ + " " + Params$
								AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
								A2.ActorInstance = AInstance\FirstInZone
								While A2 <> Null
									If A2\RNID > 0
										If PlayerIgnoring(A2, AI) = 0
											RN_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
										EndIf
									EndIf
									A2 = A2\NextInZone
								Wend
								If AInstance\Area = GameArea Then AddTextAreaText(Game\ChatText, Pa$ + Chr$(13))
							Case "YELL"
								Pa$ = Chr$(253) + "<" + AI\Name$ + "> " + Params$
								For A2.ActorInstance = Each ActorInstance
									If A2\RNID > 0
										If PlayerIgnoring(A2, AI) = 0
											RN_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
										EndIf
									EndIf
								Next
								AddTextAreaText(Game\ChatText, Pa$ + Chr$(13))
							Case "GM"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Pa$ = Chr$(254) + "<GM> <" + AI\Name$ + "> " + Params$
									For A2.ActorInstance = Each ActorInstance
										If A2\RNID > 0
											A.Account = Object.Account(A2\Account)
											If A\IsDM = True Then RN_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
										EndIf
									Next
								EndIf
							Case "G"
								If AI\TeamID > 0
									Pa$ = Chr$(251) + "<G> <" + AI\Name$ + "> " + Params$
									For A2.ActorInstance = Each ActorInstance
										If A2\RNID > 0
											If A2\TeamID = AI\TeamID Then RN_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
										EndIf
									Next
								EndIf
							Case "P"
								Party.Party = Object.Party(AI\PartyID)
								If Party <> Null
									Pa$ = Chr$(251) + "<PARTY> <" + AI\Name$ + "> " + Params$
									For i = 0 To 7
										If Party\Player[i] <> Null
											If Party\Player[i] <> AI Then RN_Send(Host, Party\Player[i]\RNID, P_ChatMessage, Pa$, True)
										EndIf
									Next
								EndIf
							Case "PM"
								Name$ = Upper$(Split$(Params$, 1, ","))
								Params$ = Split$(Params$, 2, ",")
								For A2.ActorInstance = Each ActorInstance
									If A2\RNID > 0
										If Upper$(A2\Name$) = Name$
											If PlayerIgnoring(A2, AI) = 0
												RN_Send(Host, A2\RNID, P_ChatMessage, Chr$(252) + AI\Name$ + ": " + Params$, True)
											EndIf
											Exit
										EndIf
									EndIf
								Next
							Case "TRADE"
								; Player has been offered a trade and is accepting
								If AI\IsTrading = 3
									AI\IsTrading = 4
									; Character to trade with has been deleted
									If AI\TradingActor = Null
										AI\IsTrading = 0
										Goto OfferTrade
									; Or left the game
									ElseIf AI\TradingActor\RNID < 1
										AI\IsTrading = 0
										Goto OfferTrade
									; Both players are here and have accepted
									ElseIf AI\TradingActor\IsTrading = 4
										RN_Send(Host, AI\RNID, P_OpenTrading, "11P", True)
										RN_Send(Host, AI\TradingActor\RNID, P_OpenTrading, "11P", True)
									EndIf
								; Not currently trading, offer a trade
								ElseIf AI\IsTrading = 0
									.OfferTrade
									If Params$ <> ""
										A2.ActorInstance = FindPlayerFromName.ActorInstance(Params$)
										If A2 <> Null
											If A2\RNID > 0
												If A2\IsTrading = 0
													If PlayerIgnoring(A2, AI) = 0
														AI\IsTrading = 4
														A2\IsTrading = 3
														AI\TradingActor = A2
														A2\TradingActor = AI
														Pa$ = LanguageString$(LS_TradeInviteInstruction)
														RN_Send(Host, A2\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_TradeInvite) + " " + AI\Name$ + Pa$, True)
													EndIf
												EndIf
											Else
												RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + Params$ + " " + LanguageString$(LS_XIsOffline), True)
											EndIf
										Else
											RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PlayerNotFound) + " " + Params$, True)
										EndIf
									EndIf
								EndIf
							Case "ALLPLAYERS"
								Players = 0
								For A2.ActorInstance = Each ActorInstance
									If A2\RNID > 0 Then Players = Players + 1
								Next
								RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PlayersInGame) + " " + Str$(Players - 1), True)
							Case "PLAYERS"
								Players = 0
								AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
								A2.ActorInstance = AInstance\FirstInZone
								While A2 <> Null
									If A2\RNID > 0 Then Players = Players + 1
									A2 = A2\NextInZone
								Wend
								RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PlayersInZone) + " " + Str$(Players - 1), True)
							Case "WARP"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Ar.Area = FindArea(Trim$(Split$(Params$, 1, ",")))
									Instance = Split$(Params$, 2, ",")
									For i = 0 To 99
										If Ar\PortalName$[i] <> ""
											SetArea(AI, Ar, Instance, -1, i)
											Exit
										EndIf
									Next
								EndIf
							Case "WARPOTHER"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Name$ = Upper$(Trim$(Split$(Params$, 1, ",")))
									For A2.ActorInstance = Each ActorInstance
										If A2\RNID > 0
											If Upper$(A2\Name$) = Name$
												Ar.Area = FindArea(Trim$(Split$(Params$, 2, ",")))
												Instance = Split$(Params$, 3, ",")
												For i = 0 To 99
													If Ar\PortalName$[i] <> ""
														SetArea(A2, Ar, Instance, -1, i)
														Exit
													EndIf
												Next
												Exit
											EndIf
										EndIf
									Next
								EndIf
							Case "ABILITY"
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									Params$ = Upper$(Params$)
									Name$ = Trim$(SafeSplit$(Params$, 1, ","))
									Level = Trim$(SafeSplit$(Params$, 2, ","))
									For Sp.Spell = Each Spell
										If Upper$(Sp\Name$) = Name$ Then AddSpell(AI, Sp\ID, Level) : Exit
									Next
								EndIf
							Case "GIVE"
								; Make sure it's a GM account
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									; Find the requested item
									Params$ = Upper$(Params$)
									For It.Item = Each Item
										If Upper$(It\Name$) = Params$
											; Create the item
											II.ItemInstance = CreateItemInstance(It)
											II\Assignment = 1
											II\AssignTo = AI
											; Ask client to specify a slot to put it in
											Pa$ = RN_StrFromInt$(It\ID, 2) + RN_StrFromInt$(II\Assignment, 2)
											RN_Send(Host, AI\RNID, P_InventoryUpdate, "G" + RN_StrFromInt$(Handle(II), 4) + Pa$, True)
											Exit
										EndIf
									Next
								EndIf
							Case "WEATHER"
								Params$ = Trim$(Upper$(Params$))
								; Make sure it's a GM account
								A.Account = Object.Account(AI\Account)
								If A\IsDM = True
									AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
									; Choose new weather
									Select Params$
										Case "SUN", "SUNNY", "NORMAL"
											AInstance\CurrentWeather = W_Sun
										Case "RAIN", "RAINY"
											AInstance\CurrentWeather = W_Rain
										Case "SNOW", "SNOWY"
											AInstance\CurrentWeather = W_Snow
										Case "FOG", "FOGGY"
											AInstance\CurrentWeather = W_Fog
										Case "WIND", "WINDY"
											AInstance\CurrentWeather = W_Wind
										Case "STORM", "STORMY", "THUNDER", "LIGHTNING"
											AInstance\CurrentWeather = W_Storm
									End Select
									AInstance\CurrentWeatherTime = Rand(2500, 10000)

									; Tell players in this area
									Pa$ = RN_StrFromInt$(Handle(AInstance), 4) + RN_StrFromInt$(AInstance\CurrentWeather, 1)
									AI.ActorInstance = AInstance\FirstInZone
									While AI <> Null
										If AI\RNID > 0 Then RN_Send(Host, AI\RNID, P_WeatherChange, Pa$, True)
										AI = AI\NextInZone
									Wend

									; Force an update for all areas with weather linked to this area
									If AInstance\ID = 0
										For Ar.Area = Each Area
											If Ar\WeatherLinkArea = AInstance\Area
												For i = 0 To 99
													If Ar\Instances[i] <> Null
														Ar\Instances[i]\CurrentWeatherTime = 0
													EndIf
												Next
											EndIf
										Next
									EndIf
								EndIf
							Case "TIME"
								Hour$ = Str$(TimeH)
								If Len(Hour$) = 1 Then Hour$ = "0" + Hour$
								Minute$ = Str$(TimeM)
								If Len(Minute$) = 1 Then Minute$ = "0" + Minute$
								RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Time: " + Hour$ + ":" + Minute$, True)
							Case "DATE"
								Month = GetMonth()
								If Month = 0
									Date$ = Str$(Day + 1)
								Else
									Date$ = Str$((Day - MonthStartDay(Month)) + 1)
								EndIf
								If Right$(Date$, 1) = "1" And Date$ <> "11"
									Date$ = Date$ + "st"
								ElseIf Right$(Date$, 1) = "2" And Date$ <> "12"
									Date$ = Date$ + "nd"
								ElseIf Right$(Date$, 1) = "3" And Date$ <> "13"
									Date$ = Date$ + "rd"
								Else
									Date$ = Date$ + "th"
								EndIf
								Date$ = Chr$(254) + MonthName$(Month) + " " + Date$ + ", " + Str$(Year)
								RN_Send(Host, AI\RNID, P_ChatMessage, Date$, True)
							Case "SEASON"
								RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_Season) + " " + SeasonName$(GetSeason()), True)
							Default
								RunScript("In-game Commands", Command$, AI, Null, Params$)
						End Select
					; General chat - forward to other people in same area
					Else
						Pa$ = "<" + AI\Name$ + "> " + M\MessageData$
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						A2.ActorInstance = AInstance\FirstInZone
						While A2 <> Null
							If A2\RNID > 0 Then RN_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
							A2 = A2\NextInZone
						Wend
						If AInstance\Area = GameArea
							AddTextAreaText(Game\ChatText, Pa$ + Chr$(13))
							If ChatLoggingMode > 0 Then WriteLog(ChatLog, Pa$, True, True)
						ElseIf ChatLoggingMode = 2
							WriteLog(ChatLog, Pa$, True, True)
						EndIf
					EndIf
				EndIf

			; Scenery item selected
			Case P_SelectScenery
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					ID = RN_IntFromStr(Left$(M\MessageData$, 2))
					If ID > 0 And ID <= 500
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						If AInstance\OwnedScenery[ID - 1] <> Null
							A.Account = Object.Account(AI\Account)
							If AInstance\OwnedScenery[ID - 1]\AccountName$ = A\User$
								If AInstance\OwnedScenery[ID - 1]\CharNumber = A\LoggedOn
									; Allow scenery animation
									RN_Send(Host, AI\RNID, P_SelectScenery, Right$(M\MessageData$, 4), True)

									; Open trading window
									If AI\IsTrading = 0 And AInstance\OwnedScenery[ID - 1]\InventorySize > 0
										AI\IsTrading = 1
										AI\TradeResult$ = RN_StrFromInt$(ID - 1, 2) + RN_StrFromInt$(Handle(Ar), 4)
										Pa$ = "S"
										For i = 0 To AInstance\OwnedScenery[ID - 1]\InventorySize - 1
											If AInstance\OwnedScenery[ID - 1]\Inventory\Amounts[i] > 0 And AInstance\OwnedScenery[ID - 1]\Inventory\Items[i] <> Null
												CopiedII.ItemInstance = CopyItemInstance(AInstance\OwnedScenery[ID - 1]\Inventory\Items[i])
												CopiedII\Assignment = AInstance\OwnedScenery[ID - 1]\Inventory\Amounts[i]
												CopiedII\AssignTo = AI
												Pa$ = Pa$ + ItemInstanceToString$(AInstance\OwnedScenery[ID - 1]\Inventory\Items[i])
												Pa$ = Pa$ + RN_StrFromInt$(AInstance\OwnedScenery[ID - 1]\Inventory\Amounts[i], 2) + RN_StrFromInt$(Handle(CopiedII), 4)
											EndIf
										Next
										If Len(Pa$) < 999
											RN_Send(Host, AI\RNID, P_OpenTrading, "11" + Pa$, True)
										ElseIf Len(Pa$) < 1998
											SendQueued(Host, AI\RNID, P_OpenTrading, "12" + Left$(Pa$, 998), True)
											SendQueued(Host, AI\RNID, P_OpenTrading, "22" + Mid$(Pa$, 999), True)
										Else
											SendQueued(Host, AI\RNID, P_OpenTrading, "13" + Left$(Pa$, 998), True)
											SendQueued(Host, AI\RNID, P_OpenTrading, "23" + Mid$(Pa$, 999, 998), True)
											SendQueued(Host, AI\RNID, P_OpenTrading, "33" + Mid$(Pa$, 1998), True)
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf

			; Update player -> player trading
			Case P_UpdateTrading
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					If AI\IsTrading = 4 And AI\TradingActor <> Null
						Slot = RN_IntFromStr(Left$(M\MessageData$, 1))
						Amount = RN_IntFromStr(Mid$(M\MessageData$, 2, 2))
						Pa$ = M\MessageData$
						If Amount > 0 Then Pa$ = Pa$ + ItemInstanceToString$(AI\Inventory\Items[Slot + SlotI_Backpack])
						RN_Send(Host, AI\TradingActor\RNID, P_UpdateTrading, Pa$, True)
					EndIf
				EndIf

			; Trading complete
			Case P_OpenTrading
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					; Accept trading
					If Len(M\MessageData$) > 0
						; Player -> NPC or player -> scenery trading
						If AI\IsTrading = 1
							AI\IsTrading = 0
							Change = 0

							If AI\TradeResult$ <> ""
								ID = RN_IntFromStr(Left$(AI\TradeResult$, 2))
								AInstance.AreaInstance = Object.AreaInstance(RN_IntFromStr(Right$(AI\TradeResult$, 4)))
								Owned.OwnedScenery = AInstance\OwnedScenery[ID]
							EndIf

							; Sold items
							Offset = 193
							For i = 0 To 31
								SlotID = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
								Amount = RN_IntFromStr(Mid$(M\MessageData$, Offset + 1, 2))
								Offset = Offset + 3
								If SlotID > 0
									If Amount > AI\Inventory\Amounts[SlotID] Then Amount = AI\Inventory\Amounts[SlotID]
									If AI\Inventory\Items[SlotID] <> Null And Amount > 0
										; Alter cost
										If Owned = Null
											Change = Change + (AI\Inventory\Items[SlotID]\Item\Value * Amount)
										; Add item to scenery inventory if applicable
										Else
											For j = 0 To Owned\InventorySize - 1
												If Owned\Inventory\Items[j] = Null
													Owned\Inventory\Items[j] = CopyItemInstance(AI\Inventory\Items[SlotID])
													Owned\Inventory\Amounts[j] = Amount
													Exit
												ElseIf ItemInstancesIdentical(AI\Inventory\Items[SlotID], Owned\Inventory\Items[j])
													Owned\Inventory\Amounts[j] = Owned\Inventory\Amounts[j] + Amount
													Exit
												EndIf
											Next
										EndIf
										; Remove item
										AI\Inventory\Amounts[SlotID] = AI\Inventory\Amounts[SlotID] - Amount
										If AI\Inventory\Amounts[SlotID] <= 0 Then FreeItemInstance(AI\Inventory\Items[SlotID])
										; Tell player if required
										If AI\RNID > 0
											Pa$ = RN_StrFromInt$(SlotID, 1) + RN_StrFromInt$(Amount, 2)
											RN_Send(Host, AI\RNID, P_InventoryUpdate, "T" + Pa$, True)
										EndIf
									EndIf
								EndIf
							Next
							; Bought items
							Offset = 1
							For i = 0 To 31
								II.ItemInstance = Object.ItemInstance(RN_IntFromStr(Mid$(M\MessageData$, Offset, 4)))
								Amount = RN_IntFromStr(Mid$(M\MessageData$, Offset + 4, 2))
								Offset = Offset + 6
								If II <> Null And Amount > 0
									If II\Assignment > 0 And II\AssignTo = AI
										If Amount < II\Assignment Then II\Assignment = Amount
										; Alter cost
										If Owned = Null
											OldChange = Change
											Change = Change - (II\Item\Value * II\Assignment)
										; Remove from scenery inventory if applicable
										Else
											RemoveAmount = II\Assignment
											For j = 0 To Owned\InventorySize - 1
												If Owned\Inventory\Items[j] <> Null
													If ItemInstancesIdentical(II, Owned\Inventory\Items[j])
														If Owned\Inventory\Amounts[j] >= RemoveAmount
															Owned\Inventory\Amounts[j] = Owned\Inventory\Amounts[j] - RemoveAmount
															RemoveAmount = 0
															If Owned\Inventory\Amounts[j] = 0 Then FreeItemInstance(Owned\Inventory\Items[j])
														Else
															RemoveAmount = RemoveAmount - Owned\Inventory\Amounts[j]
															Owned\Inventory\Amounts[j] = 0
															FreeItemInstance(Owned\Inventory\Items[j])
														EndIf
														If RemoveAmount = 0 Then Exit
													EndIf
												EndIf
											Next
										EndIf
										; Prevent cheating
										If AI\Gold + Change >= 0
											; Ask client to specify a slot to put it in
											Pa$ = RN_StrFromInt$(II\Item\ID, 2) + RN_StrFromInt$(II\Assignment, 2)
											RN_Send(Host, AI\RNID, P_InventoryUpdate, "G" + RN_StrFromInt$(Handle(II), 4) + Pa$, True)
										Else
											Change = OldChange
											FreeItemInstance(II)
											Exit
										EndIf
									EndIf
								EndIf
							Next

							; Adjust gold level
							If Owned = Null
								If Change <> 0
									AI\Gold = AI\Gold + Change
									If Change > 0
										Pa$ = "U" + RN_StrFromInt$(Change, 4)
									Else
										Pa$ = "D" + RN_StrFromInt$(Abs(Change), 4)
									EndIf
									RN_Send(Host, AI\RNID, P_GoldChange, Pa$, True)
								EndIf
							EndIf
						; Player -> player trading
						ElseIf AI\IsTrading = 4
							AI\IsTrading = 5
							AI\TradeResult$ = M\MessageData$
							; Character to trade with has been deleted
							If AI\TradingActor = Null
								AI\TradeResult$ = ""
								AI\IsTrading = 0
								RN_Send(Host, AI\RNID, P_CloseTrading, "", True)
							; Or left the game
							ElseIf AI\TradingActor\RNID < 1
								AI\TradeResult$ = ""
								AI\IsTrading = 0
								AI\TradingActor = Null
								RN_Send(Host, AI\RNID, P_CloseTrading, "", True)
							; Both players are here and have accepted
							ElseIf AI\TradingActor\IsTrading = 5
								A2.ActorInstance = AI\TradingActor

								; Compare what players expect with what they are getting, to prevent cheating
								Valid = True
								A1Cost = RN_IntFromStr(Left$(AI\TradeResult$, 4)) * -1
								A2Cost = RN_IntFromStr(Left$(A2\TradeResult$, 4))
								If A1Cost <> A2Cost Then Valid = False
								For i = 0 To 31
									A1SoldAmount = RN_IntFromStr(Mid$(AI\TradeResult$, 101 + (i * 2), 2))
									If A1SoldAmount > 0
										For j = 0 To 31
											If RN_IntFromStr(Mid$(A2\TradeResult$, 5 + (j * 3), 1)) = i
												Amount = RN_IntFromStr(Mid$(A2\TradeResult$, 6 + (j * 3), 2))
												If Amount <> A1SoldAmount Then Valid = False
												Exit
											EndIf
										Next
									EndIf
									A2SoldAmount = RN_IntFromStr(Mid$(A2\TradeResult$, 101 + (i * 2), 2))
									If A2SoldAmount > 0
										For j = 0 To 31
											If RN_IntFromStr(Mid$(AI\TradeResult$, 5 + (j * 3), 1)) = i
												Amount = RN_IntFromStr(Mid$(AI\TradeResult$, 6 + (j * 3), 2))
												If Amount <> A2SoldAmount Then Valid = False
												Exit
											EndIf
										Next
									EndIf
								Next

								If Valid = True
									; Swap money
									AI\Gold = AI\Gold + A2Cost
									A2\Gold = A2\Gold - A2Cost
									If A2Cost > 0
										RN_Send(Host, AI\RNID, P_GoldChange, "U" + RN_StrFromInt$(A2Cost, 4), True)
										RN_Send(Host, A2\RNID, P_GoldChange, "D" + RN_StrFromInt$(A2Cost, 4), True)
									Else
										RN_Send(Host, AI\RNID, P_GoldChange, "D" + RN_StrFromInt$(A2Cost, 4), True)
										RN_Send(Host, A2\RNID, P_GoldChange, "U" + RN_StrFromInt$(A2Cost, 4), True)
									EndIf

									; Swap items
									For i = 0 To 31
										SlotID = i + SlotI_Backpack
										; Actor 1
										Amount = RN_IntFromStr(Mid$(AI\TradeResult$, 101 + (i * 2), 2))
										If Amount > AI\Inventory\Amounts[SlotID] Then Amount = AI\Inventory\Amounts[SlotID]
										If AI\Inventory\Items[SlotID] <> Null And Amount > 0
											GiveItem.ItemInstance = CopyItemInstance(AI\Inventory\Items[SlotID])
											; Remove item
											AI\Inventory\Amounts[SlotID] = AI\Inventory\Amounts[SlotID] - Amount
											If AI\Inventory\Amounts[SlotID] <= 0 Then FreeItemInstance(AI\Inventory\Items[SlotID])
											RN_Send(Host, AI\RNID, P_InventoryUpdate, "T" + RN_StrFromInt$(SlotID, 1) + RN_StrFromInt$(Amount, 2), True)
											; Give to new player
											GiveItem\Assignment = Amount
											GiveItem\AssignTo = A2
											Pa$ = RN_StrFromInt$(GiveItem\Item\ID, 2) + RN_StrFromInt$(GiveItem\Assignment, 2)
											RN_Send(Host, A2\RNID, P_InventoryUpdate, "G" + RN_StrFromInt$(Handle(GiveItem), 4) + Pa$, True)
										EndIf
										; Actor 2
										Amount = RN_IntFromStr(Mid$(A2\TradeResult$, 101 + (i * 2), 2))
										If Amount > A2\Inventory\Amounts[SlotID] Then Amount = A2\Inventory\Amounts[SlotID]
										If A2\Inventory\Items[SlotID] <> Null And Amount > 0
											GiveItem.ItemInstance = CopyItemInstance(A2\Inventory\Items[SlotID])
											; Remove item
											A2\Inventory\Amounts[SlotID] = A2\Inventory\Amounts[SlotID] - Amount
											If A2\Inventory\Amounts[SlotID] <= 0 Then FreeItemInstance(A2\Inventory\Items[SlotID])
											RN_Send(Host, A2\RNID, P_InventoryUpdate, "T" + RN_StrFromInt$(SlotID, 1) + RN_StrFromInt$(Amount, 2), True)
											; Give to new player
											GiveItem\Assignment = Amount
											GiveItem\AssignTo = AI
											Pa$ = RN_StrFromInt$(GiveItem\Item\ID, 2) + RN_StrFromInt$(GiveItem\Assignment, 2)
											RN_Send(Host, AI\RNID, P_InventoryUpdate, "G" + RN_StrFromInt$(Handle(GiveItem), 4) + Pa$, True)
										EndIf
									Next
								EndIf

								; End trading mode for both players
								AI\TradeResult$ = ""
								AI\IsTrading = 0
								AI\TradingActor = Null
								RN_Send(Host, AI\RNID, P_CloseTrading, "", True)
								A2\TradeResult$ = ""
								A2\IsTrading = 0
								A2\TradingActor = Null
								RN_Send(Host, A2\RNID, P_CloseTrading, "", True)
							EndIf
						EndIf
					; Cancel trading
					Else
						If AI\IsTrading = 5 And AI\TradingActor <> Null
							If AI\TradingActor\RNID > 0 Then RN_Send(Host, AI\TradingActor\RNID, P_CloseTrading, "", True)
						EndIf
						AI\IsTrading = 0
					EndIf
				EndIf

			; Jump
			Case P_Jump
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					If AI\Mount = Null
						; Tell other players in the same area
						Pa$ = RN_StrFromInt$(AI\RuntimeID, 2)
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						A2.ActorInstance = AInstance\FirstInZone
						While A2 <> Null
							If A2\RNID > 0 Then RN_Send(Host, A2\RNID, P_Jump, Pa$, True)
							A2 = A2\NextInZone
						Wend
					EndIf
				EndIf

			; Action bar update
			Case P_ActionBarUpdate
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					A.Account = Object.Account(AI\Account)
					Num = RN_IntFromStr(Mid$(M\MessageData$, 2, 1))
					If Num >= 0 And Num < 36 And A <> Null
						If A\LoggedOn > -1
							If Left$(M\MessageData$, 1) = "S"
								A\ActionBar[A\LoggedOn]\Slots$[Num] = "S" + Mid$(M\MessageData$, 3)
							ElseIf Left$(M\MessageData$, 1) = "I"
								A\ActionBar[A\LoggedOn]\Slots$[Num] = "I" + Mid$(M\MessageData$, 3, 2)
							ElseIf Left$(M\MessageData$, 1) = "N"
								A\ActionBar[A\LoggedOn]\Slots$[Num] = ""
							EndIf
						EndIf
					EndIf
				EndIf

			; Spell update
			Case P_SpellUpdate
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					Select Left$(M\MessageData$, 1)
						; Player has unmemorised a spell
						Case "U"
							If RequireMemorise
								Num = RN_IntFromStr(Mid$(M\MessageData$, 2, 2))
								For i = 0 To 9
									If AI\MemorisedSpells[i] = Num Then AI\MemorisedSpells[i] = 5000 : Exit
								Next
							EndIf
						; Player is memorising a spell
						Case "M"
							If RequireMemorise
								MS.MemorisingSpell = New MemorisingSpell
								MS\AI = AI
								MS\KnownNum = RN_IntFromStr(Mid$(M\MessageData$, 2, 2))
								MS\CreatedTime = MilliSecs()
								If MS\KnownNum < 0 Or MS\KnownNum > 999 Then Delete MS
							EndIf
						; Player is firing a spell
						Case "F"
							; Spell ID and target
							Num = RN_IntFromStr(Mid$(M\MessageData$, 2, 2))
							Context.ActorInstance = Null
							If Len(M\MessageData$) > 3
								RuntimeID = RN_IntFromStr(Mid$(M\MessageData$, 4, 2))
								Context = RuntimeIDList(RuntimeID)
							EndIf
							; Convert ID into known spell number
							Found = False
							For i = 0 To 999
								If AI\KnownSpells[i] = Num
									Num = i
									Found = True
									Exit
								EndIf
							Next
							If Found = True
								; Spell must be memorised to fire
								If RequireMemorise
									For i = 0 To 9
										If AI\MemorisedSpells[i] = Num
											Sp.Spell = SpellsList(AI\KnownSpells[Num])
											If AI\SpellCharge[i] <= 0
												RunScript(Sp\Script$, Sp\Method$, AI, Context, AI\SpellLevels[Num])
												AI\SpellCharge[i] = Sp\RechargeTime
											Else
												RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_AbilityNotRecharged), True)
											EndIf
											Exit
										EndIf
									Next
								; Fire spell directly
								Else
									Sp.Spell = SpellsList(AI\KnownSpells[Num])
									If AI\SpellCharge[Num] <= 0
										RunScript(Sp\Script$, Sp\Method$, AI, Context, AI\SpellLevels[Num])
										AI\SpellCharge[Num] = Sp\RechargeTime
									Else
										RN_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_AbilityNotRecharged), True)
									EndIf
								EndIf
							EndIf
					End Select
				EndIf

			; Progress bar message
			Case P_ProgressBar
				If Left$(M\MessageData$, 1) = "C"
					S.ScriptInstance = Object.ScriptInstance(RN_IntFromStr(Mid$(M\MessageData$, 2, 4)))
					If S <> Null
						S\WaitResult$ = Str$(RN_IntFromStr(Mid$(M\MessageData$, 6)))
					Else
						RN_Send(Host, M\FromID, P_ProgressBar, "D" + Mid$(M\MessageData$, 6), True)
					EndIf
				EndIf

			; Dialog message
			Case P_Dialog
				Select Left$(M\MessageData$, 1)
					; New dialog created
					Case "N"
						S.ScriptInstance = Object.ScriptInstance(RN_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If S <> Null
							S\WaitResult$ = Str$(RN_IntFromStr(Mid$(M\MessageData$, 6)))
						Else
							RN_Send(Host, M\FromID, P_Dialog, "C" + Mid$(M\MessageData$, 6), True)
						EndIf
					; Dialog text received
					Case "T"
						S.ScriptInstance = Object.ScriptInstance(RN_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If S <> Null Then S\WaitResult$ = "0"
					; Dialog option picked
					Case "O"
						S.ScriptInstance = Object.ScriptInstance(RN_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If S <> Null Then S\WaitResult$ = RN_IntFromStr(Mid$(M\MessageData$, 6, 1))
				End Select

			; Text input reply
			Case P_ScriptInput
				S.ScriptInstance = Object.ScriptInstance(RN_IntFromStr(Mid$(M\MessageData$, 1, 4)))
				If S <> Null Then S\WaitResult$ = Chr$(34) + Mid$(M\MessageData$, 5) + Chr$(34)

			; A player ate an item
			Case P_EatItem
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					Slot = RN_IntFromStr(Mid$(M\MessageData$, 1, 1))
					Amount = RN_IntFromStr(Mid$(M\MessageData$, 2, 2))
					If Slot >= 0 And Slot < 50 And Amount > 0
						If AI\Inventory\Items[Slot] <> Null And AI\Inventory\Amounts[Slot] >= Amount
							If AI\Inventory\Items[Slot]\Item\ItemType = I_Potion Or AI\Inventory\Items[Slot]\Item\ItemType = I_Ingredient
								If Upper$(AI\Actor\Class$) = Upper$(AI\Inventory\Items[Slot]\Item\ExclusiveClass$) Or Len(AI\Inventory\Items[Slot]\Item\ExclusiveClass$) = 0
									If Upper$(AI\Actor\Race$) = Upper$(AI\Inventory\Items[Slot]\Item\ExclusiveRace$) Or Len(AI\Inventory\Items[Slot]\Item\ExclusiveRace$) = 0
										; Create buff
										EffectName$ = AI\Inventory\Items[Slot]\Item\Name$
										Found = False
										For AE.ActorEffect = Each ActorEffect
											If AE\Owner = AI
												If Upper$(AE\Name$) = Upper$(EffectName$)
													FoundAE.ActorEffect = AE
													Found = True
													Exit
												EndIf
											EndIf
										Next
										If Found = False
											FoundAE = New ActorEffect
											FoundAE\Attributes = New Attributes
											FoundAE\Name$ = EffectName$
											FoundAE\Owner = AI
											Pa$ = RN_StrFromInt$(Handle(FoundAE), 4) + RN_StrFromInt$(AI\Inventory\Items[Slot]\Item\ThumbnailTexID, 2) + FoundAE\Name$
											RN_Send(Host, AI\RNID, P_ActorEffect, "A" + Pa$, True)
										EndIf
										FoundAE\CreatedTime = MilliSecs()
										FoundAE\Length = AI\Inventory\Items[Slot]\Item\EatEffectsLength * 1000
										For i = 0 To 39
											If AI\Inventory\Items[Slot]\Attributes\Value[i] <> 0
												Old = FoundAE\Attributes\Value[i]
												FoundAE\Attributes\Value[i] = AI\Inventory\Items[Slot]\Attributes\Value[i]
												Pa$ = RN_StrFromInt$(i, 1) + RN_StrFromInt$(FoundAE\Attributes\Value[i] - Old, 4)
												FoundAE\Owner\Attributes\Value[i] = FoundAE\Owner\Attributes\Value[i] + (FoundAE\Attributes\Value[i] - Old)
												RN_Send(Host, FoundAE\Owner\RNID, P_ActorEffect, "E" + Pa$, True)
											EndIf
										Next
										; Remove item
										AI\Inventory\Amounts[Slot] = AI\Inventory\Amounts[Slot] - Amount
										If AI\Inventory\Amounts[Slot] <= 0 Then FreeItemInstance(AI\Inventory\Items[Slot])
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf

			; A player used an item
			Case P_ItemScript
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And (Len(M\MessageData$) = 1 Or Len(M\MessageData$) = 3)
					SlotIndex = RN_IntFromStr(Left$(M\MessageData$, 1))
					If Len(M\MessageData$) = 3
						A2.ActorInstance = RuntimeIDList(RN_IntFromStr(Mid$(M\MessageData$, 2)))
					Else
						A2 = Null
					EndIf
					If SlotIndex >= 0 And SlotIndex < 50
						If AI\Inventory\Items[SlotIndex] <> Null
							If AI\Inventory\Items[SlotIndex]\Item\ExclusiveClass$ = "" Or Upper$(AI\Actor\Race$) = Upper$(AI\Inventory\Items[SlotIndex]\Item\ExclusiveClass$)
								If AI\Inventory\Items[SlotIndex]\Item\ExclusiveRace$ = "" Or Upper$(AI\Actor\Race$) = Upper$(AI\Inventory\Items[SlotIndex]\Item\ExclusiveRace$)
									If AI\Inventory\Items[SlotIndex]\Item\Script$ <> ""
										If AI\Inventory\Amounts[SlotIndex] > 0
											If AI\Inventory\Items[SlotIndex]\Item\Method$ = ""
												RunScript(AI\Inventory\Items[SlotIndex]\Item\Script$, "Main", AI, A2)
											Else
												RunScript(AI\Inventory\Items[SlotIndex]\Item\Script$, AI\Inventory\Items[SlotIndex]\Item\Method$, AI, A2)
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf

			; A player right clicked on an actor
			Case P_RightClick
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And Len(M\MessageData$) = 2
					A2.ActorInstance = RuntimeIDList(RN_IntFromStr(M\MessageData$))
					If A2 <> Null
						XDist# = Abs(AI\X# - A2\X#)
						ZDist# = Abs(AI\Z# - A2\Z#)
						Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
						If Dist# < 50.0
							; Start right click script
							If A2\Script$ <> ""
								Running = False
								Script.Script = FindScript(A2\Script$)
								For Si.ScriptInstance = Each ScriptInstance
									If Si\S = Script
										If Si\AI = AI Then Running = True : Exit
									EndIf
								Next
								If Running = False Then RunScript(A2\Script$, "Main", AI, A2)
							; No script to run, if actor can be ridden, mount it
							ElseIf A2\Actor\Rideable = True
								If (A2\Leader = Null Or A2\Leader = AI) And AI\Mount = Null
									AI\Mount = A2
									A2\Rider = AI
									A2\AIMode = AI_None
									RunScript("Mount", "Mount", AI, A2)
								EndIf
							EndIf
							; Continue any paused scripts waiting for this conversation
							For PS.PausedScript = Each PausedScript
								If PS\Reason = 4
									If PS\ReasonActor = AI And PS\ReasonContextActor = A2
										PS\S\WaitResult$ = "1"
										Delete PS
									EndIf
								EndIf
							Next
						EndIf
					EndIf
				EndIf

			; A player has attacked something
			Case P_AttackActor
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And Len(M\MessageData$) = 2
					; Check combat delay and whether actor is riding a mount, to prevent cheating
					If MilliSecs() - AI\LastAttack >= CombatDelay And AI\Mount = Null
						A2.ActorInstance = RuntimeIDList(RN_IntFromStr(M\MessageData$))
						If A2 <> Null
							AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
							If A2\RNID < 0 Or AInstance\Area\PvP = True
								ActorAttack(AI, A2)
								AI\AITarget = A2
							EndIf
						EndIf
					EndIf
				EndIf

			; Inventory update
			Case P_InventoryUpdate
				Select Left$(M\MessageData$, 1)
					; Request to pick up a dropped item
					Case "P"
						D.DroppedItem = Object.DroppedItem(RN_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If D <> Null
							AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
							If AI <> Null
								SlotI = RN_IntFromStr(Mid$(M\MessageData$, 6, 1))
								If AI\Inventory\Items[SlotI] = Null Or (ItemInstancesIdentical(D\Item, AI\Inventory\Items[SlotI]) And D\Item\Item\Stackable = True And SlotI >= SlotI_Backpack)
									If SlotsMatch(D\Item\Item, SlotI) And ActorHasSlot(AI\Actor, SlotI, D\Item\Item)
										; Put into player's inventory
										If AI\Inventory\Items[SlotI] <> Null
											Delete AI\Inventory\Items[SlotI]
										Else
											AI\Inventory\Amounts[SlotI] = 0
										EndIf
										AI\Inventory\Items[SlotI] = D\Item
										AI\Inventory\Amounts[SlotI] = AI\Inventory\Amounts[SlotI] + D\Amount
										If SlotI < SlotI_Backpack Then SendEquippedUpdate(AI)

										; Tell this player he got it
										RN_Send(Host, AI\RNID, P_InventoryUpdate, "R" + RN_StrFromInt$(Handle(D)) + RN_StrFromInt$(SlotI, 1), True)

										; Tell other players it's gone
										Pa$ = "P" + RN_StrFromInt$(Handle(D))
										AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
										A2.ActorInstance = AInstance\FirstInZone
										While A2 <> Null
											If A2\RNID > 0
												If A2 <> AI Then RN_Send(Host, A2\RNID, P_InventoryUpdate, Pa$, True)
											EndIf
											A2 = A2\NextInZone
										Wend

										; Delete it
										Delete D
									EndIf
								EndIf
							EndIf
						EndIf
					; Item dropped
					Case "D"
						AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
						If AI <> Null
							Slot = RN_IntFromStr(Mid$(M\MessageData$, 2, 1))
							Amount = RN_IntFromStr(Mid$(M\MessageData$, 3, 2))
							Result = InventoryDrop(AI, Slot, Amount, False)
							If Result <> 0
								SendEquippedUpdate(AI)
								; Create item on floor
								D.DroppedItem = New DroppedItem
								D\Item = Object.ItemInstance(Result)
								D\Amount = Amount
								D\X# = AI\X#
								D\Y# = AI\Y#
								D\Z# = AI\Z#
								D\ServerHandle = AI\ServerArea
								; Tell other players in the area
								Pa$ = "D" + RN_StrFromInt$(Amount, 2) + RN_StrFromFloat$(D\X#) + RN_StrFromFloat$(D\Y#) + RN_StrFromFloat$(D\Z#)
								Pa$ = Pa$ + RN_StrFromInt$(Handle(D), 4) + ItemInstanceToString$(D\Item)
								AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
								A2.ActorInstance = AInstance\FirstInZone
								While A2 <> Null
									If A2\RNID > 0 Then RN_Send(Host, A2\RNID, P_InventoryUpdate, Pa$, True)
									A2 = A2\NextInZone
								Wend
							EndIf
						EndIf
					; Reply to a given item message
					Case "G"
						AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
						II.ItemInstance = Object.ItemInstance(RN_IntFromStr(Mid$(M\MessageData$, 3, 4)))
						If II <> Null And AI <> Null
							If II\Assignment > 0
								If Mid$(M\MessageData$, 2, 1) = "Y"
									SlotI = RN_IntFromStr(Right$(M\MessageData$, 1))
									If AI\Inventory\Items[SlotI] = Null Or (ItemInstancesIdentical(II, AI\Inventory\Items[SlotI]) And II\Item\Stackable = True And SlotI >= SlotI_Backpack)
										If SlotsMatch(II\Item, SlotI) And ActorHasSlot(AI\Actor, SlotI, II\Item)
											If AI\Inventory\Items[SlotI] <> Null
												Delete AI\Inventory\Items[SlotI]
											Else
												AI\Inventory\Amounts[SlotI] = 0
											EndIf
											AI\Inventory\Items[SlotI] = II
											AI\Inventory\Amounts[SlotI] = AI\Inventory\Amounts[SlotI] + II\Assignment
											II\Assignment = 0
											If SlotI < SlotI_Backpack Then SendEquippedUpdate(AI)
										EndIf
									EndIf
								Else
									Delete II
								EndIf
							EndIf
						EndIf
					; Swap/add
					Case "S", "A"
						RuntimeID = RN_IntFromStr(Mid$(M\MessageData$, 2, 2))
						SlotA = RN_IntFromStr(Mid$(M\MessageData$, 4, 1))
						SlotB = RN_IntFromStr(Mid$(M\MessageData$, 5, 1))
						Amount = RN_IntFromStr(Mid$(M\MessageData$, 6, 2))
						AI.ActorInstance = RuntimeIDList(RuntimeID)
						AIFrom.ActorInstance = FindActorInstanceFromRNID(M\FromID)
						; Check that actor instance is valid (e.g. it isn't trying to change someone else's inventory)
						If AI <> Null And AIFrom <> Null
							IsPet = False
							Slaves = AIFrom\NumberOfSlaves
							While Slaves > 0
								For Slave.ActorInstance = Each ActorInstance
									If Slave\Leader = AIFrom
										Slaves = Slaves - 1
										If Slave = AI Then IsPet = True : Exit
									EndIf
								Next
							Wend
							If (AI = AIFrom Or IsPet = True) And (Amount = 0 Or Amount <= AI\Inventory\Amounts[SlotA])
								If Left$(M\MessageData$, 1) = "S"
									InventorySwap(AI, SlotA, SlotB, Amount, False)
									If SlotA < SlotI_Backpack Or SlotB < SlotI_Backpack Then SendEquippedUpdate(AI)
								Else
									InventoryAdd(AI, SlotA, SlotB, Amount, False)
									If SlotB < SlotI_Backpack Then SendEquippedUpdate(AI)
								EndIf
							EndIf
						EndIf
				End Select

			; Player dismounted
			Case P_Dismount
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					If AI\Mount <> Null
						RunScript("Mount", "Dismount", AI, AI\Mount)
						If AI\Mount\RNID < 0 Then AI\Mount\AIMode = AI_Patrol
						AI\Mount\Rider = Null
						AI\Mount\WalkingBackward = False
						AI\Mount\DestX# = AI\Mount\X#
						AI\Mount\DestZ# = AI\Mount\Z#
						AI\Mount = Null
					EndIf
				EndIf

			; A standard update
			Case P_StandardUpdate
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					; Player cannot move himself if he is a mount
					If AI\Rider = Null
						AI\DestX#    = RN_FloatFromStr#(Mid$(M\MessageData$, 1, 4))
						AI\DestZ#    = RN_FloatFromStr#(Mid$(M\MessageData$, 5, 4))
						AI\Y#        = RN_FloatFromStr#(Mid$(M\MessageData$, 9, 4))
						NewX#        = RN_FloatFromStr#(Mid$(M\MessageData$, 13, 4))
						NewZ#        = RN_FloatFromStr#(Mid$(M\MessageData$, 17, 4))
						AI\IsRunning = RN_IntFromStr(Mid$(M\MessageData$, 21, 1))
						AI\WalkingBackward = RN_IntFromStr(Mid$(M\MessageData$, 22, 1))

						; Players cannot run backwards, this will prevent cheaters from doing so
						If AI\WalkingBackward = True Then AI\IsRunning = False

						; Adjust to new x/z if they are less far than expected (i.e. client has undergone collision)
						; Cannot just replace values because client could then lie about the values for an easy speed cheat!
						DistX# = Abs(NewX# - AI\OldX#)
						If DistX# < Abs(AI\X# - AI\OldX#) Then AI\X# = NewX#
						DistZ# = Abs(NewZ# - AI\OldZ#)
						If DistZ# < Abs(AI\Z# - AI\OldZ#) Then AI\Z# = NewZ#
						AI\OldX# = AI\X#
						AI\OldZ# = AI\Z#

						; Move mount to same position if the player is riding one
						If AI\Mount <> Null
							AI\Mount\X# = AI\X#
							AI\Mount\Y# = AI\Y#
							AI\Mount\Z# = AI\Z#
							AI\Mount\OldX# = AI\X#
							AI\Mount\OldZ# = AI\Z#
							AI\Mount\DestX# = AI\DestX#
							AI\Mount\DestZ# = AI\DestZ#
							AI\Mount\IsRunning = AI\IsRunning
							AI\Mount\WalkingBackward = AI\WalkingBackward
						EndIf
					EndIf
				EndIf

			; A player left or timed out
			Case RN_PlayerTimedOut, RN_PlayerHasLeft, RN_PlayerKicked
				; Find which actor instance he was
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)

				If AI <> Null
					; Reset target
					AI\AITarget = Null

					; Tell other players in the same zone and remove this player from the linked list
					Pa$ = RN_StrFromInt$(AI\RuntimeID, 2)
					AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
					A2.ActorInstance = AInstance\FirstInZone
					If A2 = AI
						AInstance\FirstInZone = AI\NextInZone
						A2 = AI\NextInZone
					EndIf
					While A2 <> Null
						If A2\RNID > 0 Then RN_Send(Host, A2\RNID, P_ActorGone, Pa$, True)
						If A2\NextInZone = AI Then A2\NextInZone = AI\NextInZone
						A2 = A2\NextInZone
					Wend
					AI\ServerArea = 0

					; Cancel trading if he was trading with another player
					If AI\IsTrading >= 3
						If AI\TradingActor <> Null
							If AI\TradingActor\IsTrading >= 4 Then RN_Send(Host, AI\TradingActor\RNID, P_CloseTrading, "", True)
							AI\TradingActor\IsTrading = 0
							AI\TradingActor\TradingActor = Null
						EndIf
					EndIf

					; Dehorsify
					If AI\Rider <> Null
						AI\Rider\Mount = Null
						AI\Rider = Null
					EndIf
					If AI\Mount <> Null
						AI\Mount\Rider = Null
						AI\Mount = Null
					EndIf

					; Update timers on active actor effects
					For AE.ActorEffect = Each ActorEffect
						If AE\Owner = AI
							AE\Length = AE\Length - (MilliSecs() - AE\CreatedTime)
						EndIf
					Next

					; Delete him
					A.Account = Object.Account(AI\Account)
					If A <> Null Then SetLoginStatus(A, -1)
					; Pause any persistent scripts and stop others
					For SI.ScriptInstance = Each ScriptInstance
						If SI\AI = AI Or SI\AIContext = AI
							; Persistent
							If SI\Persistent = True
								If SI\Waiting = 0
									PS.PausedScript = New PausedScript
									PS\S = SI
									PS\ReasonActor = AI
									PS\Reason = 1
									SI\Waiting = 1
								EndIf
							; Not persistent
							Else
								FreeScriptInstance(SI)
							EndIf
						EndIf
					Next
					LeaveParty(AI)
					AI\RNID = 0
					If AI\RuntimeID > -1
						If RuntimeIDList(AI\RuntimeID) = AI Then RuntimeIDList(AI\RuntimeID) = Null
					EndIf
					AI\RuntimeID = -1
					AI\IsTrading = 0
					For II.ItemInstance = Each ItemInstance
						If II\AssignTo = AI And II\Assignment > 0 Then FreeItemInstance(II)
					Next
					
					; Remove from server display
					If AInstance\Area = GameArea
						For i = 0 To CountGadgetItems(Game\PlayersList) - 1
							Name$ = GadgetItemText$(Game\PlayersList, i)
							If Name$ = AI\Name$ + " (" + Str$(AInstance\ID) + ")" Then RemoveGadgetItem(Game\PlayersList, i) : Exit
						Next
					EndIf

					; Run logout script
					RunScript("Logout", "Main", AI, Null)
					
					; Unload account (only if using MySQL server version)
					If MySQL = True
						My_SaveAccount(A, True)
						
						; Free all data
						For j = 0 To 9
							If A\Character[j] <> Null
								FreeActorInstanceSlaves(A\Character[j])
								FreeActorInstance(A\Character[j])
								
								If A\QuestLog[j] <> Null Then Delete(A\QuestLog[j])
								If A\ActionBar[j] <> Null Then Delete(A\ActionBar[j])
							EndIf
						Next
						
						RemoveGadgetItem(Accounts\List, A\ListID)
						
						For AA.Account = Each Account
							If AA\ListID > A\ListID
								A\ListID = A\ListID -1
							EndIf
						Next
						
						Delete(A)
					EndIf
				EndIf

			; Start game request
			Case P_StartGame
				UsernameLen = RN_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$) And A\LoggedOn = -1
						Offset = 2 + UsernameLen
						PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						If A\Pass$ = Mid$(M\MessageData$, Offset + 1, PwdLen) And A\IsBanned = False
							Offset = Offset + 1 + PwdLen
							Number = Asc(Mid$(M\MessageData$, Offset, 1))

							If Number > -1 And Number < 10
								; Set his status to in game and put him in his area
								SetLoginStatus(A, Number)
								A\Character[Number]\RNID = M\FromID
								AssignRuntimeID(A\Character[Number])
								Ar.Area = FindArea(A\Character[Number]\Area$)
								SetArea(A\Character[Number], Ar, 0, -1, -1, A\Character[Number]\X#, A\Character[Number]\Y#, A\Character[Number]\Z#)
								; Run login script
								RunScript("Login", "Main", A\Character[Number], Null)
								; Send action bar data, runtime ID and XP bar level
								Pa$ = ""
								For i = 0 To 35
									Pa$ = Pa$ + RN_StrFromInt$(Len(A\ActionBar[Number]\Slots$[i]), 2) + A\ActionBar[Number]\Slots$[i]
									If (i + 1) Mod 3 = 0 And i > 0
										SendQueued(Host, M\FromID, P_StartGame, Chr$(i - 2) + Pa$, True)
										Pa$ = ""
									EndIf
								Next
						        RN_Send(Host, M\FromID, P_StartGame, RN_StrFromInt$(A\Character[Number]\RuntimeID, 2), True)
								RN_Send(Host, M\FromID, P_ChatMessage, Chr$(254) + LoginMessage$, True)
								RN_Send(Host, M\FromID, P_XPUpdate, "B" + RN_StrFromInt$(A\Character[Number]\XPBarLevel, 1), True)
								; Send active actor effects
								For AE.ActorEffect = Each ActorEffect
									If AE\Owner = A\Character[Number]
										AE\CreatedTime = MilliSecs()
										Pa$ = RN_StrFromInt$(Handle(AE), 4) + RN_StrFromInt$(AE\IconTexID, 2) + AE\Name$
										RN_Send(Host, M\FromID, P_ActorEffect, "A" + Pa$, True)
									EndIf
								Next
							Else
						        RN_Send(Host, M\FromID, P_StartGame, "N", True)
							EndIf
						; Otherwise return failure
						Else
					        RN_Send(Host, M\FromID, P_StartGame, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found or was already logged on, return failure
		        If Exists = False Then RN_Send(Host, M\FromID, P_StartGame, "N", True)

			; Fetch update files list request
			Case P_FetchUpdateFiles
				Pa$ = "" : TotalFiles = 0
				For U.UpdateFile = Each UpdateFile
					TotalFiles = TotalFiles + 1
					Add$ = RN_StrFromInt$(U\Checksum, 4) + RN_StrFromInt$(Len(U\Name$), 1) + U\Name$
					If Len(Pa$ + Add$) >= 950
						SendQueued(Host, M\FromID, P_FetchUpdateFiles, Pa$, True)
						Pa$ = Add$
					Else
						Pa$ = Pa$ + Add$
					EndIf
				Next
				SendQueued(Host, M\FromID, P_FetchUpdateFiles, Pa$ + RN_StrFromInt$(TotalFiles, 2), True)

			; Fetch actor list request
			Case P_FetchActors
				; Attributes block
				Pa$ = "A" + RN_StrFromInt$(AttributeAssignment, 1)
				For i = 0 To 39
					Pa$ = Pa$ + RN_StrFromInt$(AttributeIsSkill(i), 1) + RN_StrFromInt$(AttributeHidden(i), 1)
					Pa$ = Pa$ + RN_StrFromInt$(Len(AttributeNames$(i)), 1) + AttributeNames$(i)
				Next
				SendQueued(Host, M\FromID, P_FetchActors, Pa$, True)
				Pa$ = "D"
				For i = 0 To 19
					Pa$ = Pa$ + RN_StrFromInt$(Len(DamageTypes$(i)), 1) + DamageTypes$(i)
				Next
				SendQueued(Host, M\FromID, P_FetchActors, Pa$, True)
				; Environment block
				Pa$ = RN_StrFromInt$(Year, 4) + RN_StrFromInt$(Day, 2)
				Pa$ = Pa$ + RN_StrFromInt$(TimeH, 1) + RN_StrFromInt$(TimeM, 1) + RN_StrFromInt$(TimeFactor, 1)
				For i = 0 To 11
					Pa$ = Pa$ + RN_StrFromInt$(Len(SeasonName$(i)), 1) + SeasonName$(i)
					Pa$ = Pa$ + RN_StrFromInt$(SeasonStartDay(i), 2)
					Pa$ = Pa$ + RN_StrFromInt$(SeasonDuskH(i), 1)
					Pa$ = Pa$ + RN_StrFromInt$(SeasonDawnH(i), 1)
				Next
				For i = 0 To 19
					Pa$ = Pa$ + RN_StrFromInt$(Len(MonthName$(i)), 1) + MonthName$(i)
					Pa$ = Pa$ + RN_StrFromInt$(MonthStartDay(i), 2)
				Next
				SendQueued(Host, M\FromID, P_FetchActors, "E" + Pa$, True)
				; Item blocks
				Pa$ = "" : ItemsSent = 0 : TotalItems = 0
				For It.Item = Each Item
					TotalItems = TotalItems + 1
					ItemsSent = ItemsSent + 1
					Pa$ = Pa$ + RN_StrFromInt$(It\ID, 2) + RN_StrFromInt$(It\ItemType, 1) + RN_StrFromInt$(It\TakesDamage, 1)
					Pa$ = Pa$ + RN_StrFromInt$(It\Value, 4) + RN_StrFromInt$(It\Mass, 2) + RN_StrFromInt$(It\ThumbnailTexID, 2)
					For j = 0 To 5 : Pa$ = Pa$ + RN_StrFromInt$(It\Gubbins[j], 1) : Next
					Pa$ = Pa$ + RN_StrFromInt$(It\MMeshID, 2) + RN_StrFromInt$(It\FMeshID, 2) + RN_StrFromInt$(It\SlotType, 2)
					Pa$ = Pa$ + RN_StrFromInt$(It\Stackable, 1)
					For j = 0 To 39 : Pa$ = Pa$ + RN_StrFromInt$(It\Attributes\Value[j] + 5000, 2) : Next
					Pa$ = Pa$ + RN_StrFromInt$(Len(It\Name$), 1) + It\Name$
					Pa$ = Pa$ + RN_StrFromInt$(Len(It\ExclusiveRace$), 1) + It\ExclusiveRace$
					Pa$ = Pa$ + RN_StrFromInt$(Len(It\ExclusiveClass$), 1) + It\ExclusiveClass$
					Select It\ItemType
						Case I_Weapon
							Pa$ = Pa$ + RN_StrFromInt$(It\WeaponDamage, 2) + RN_StrFromInt$(It\WeaponDamageType, 2)
							Pa$ = Pa$ + RN_StrFromInt$(It\WeaponType, 2) + RN_StrFromFloat$(It\Range#)
						Case I_Armour
							Pa$ = Pa$ + RN_StrFromInt$(It\ArmourLevel, 2)
						Case I_Potion, I_Ingredient
							Pa$ = Pa$ + RN_StrFromInt$(It\EatEffectsLength, 2)
						Case I_Image
							Pa$ = Pa$ + RN_StrFromInt$(It\ImageID, 2)
						Case I_Other
							Pa$ = Pa$ + RN_StrFromInt$(Len(It\MiscData$), 1) + It\MiscData$
					End Select
					If ItemsSent >= 6 And It <> Last Item
						SendQueued(Host, M\FromID, P_FetchActors, "IN" + Pa$, True)
						ItemsSent = 0
						Pa$ = ""
					EndIf
				Next
				SendQueued(Host, M\FromID, P_FetchActors, "IY" + RN_StrFromInt$(TotalItems, 2) + Pa$, True)
				; Faction blocks
				Pa$ = ""
				For i = 0 To 99
					Pa$ = Pa$ + RN_StrFromInt$(Len(FactionNames$(i)), 1) + RN_StrFromInt$(i, 1) + FactionNames$(i)
					If Len(Pa$) >= 800
						SendQueued(Host, M\FromID, P_FetchActors, "F" + Pa$, True)
						Pa$ = ""
					EndIf
				Next
				If Pa$ <> "" Then SendQueued(Host, M\FromID, P_FetchActors, "F" + Pa$, True)
				; Actor blocks
				Pa$ = "" : ActorsSent = 0 : TotalActors = 0
				For Ac.Actor = Each Actor
					TotalActors = TotalActors + 1
					ActorsSent = ActorsSent + 1
					Pa$ = Pa$ + RN_StrFromInt$(Ac\ID, 2) + RN_StrFromInt$(Ac\Playable, 1) + RN_StrFromInt$(Ac\PolyCollision, 1)
					For i = 0 To 7 : Pa$ = Pa$ + RN_StrFromInt$(Ac\MeshIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RN_StrFromInt$(Ac\BeardIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RN_StrFromInt$(Ac\MaleHairIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RN_StrFromInt$(Ac\FemaleHairIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RN_StrFromInt$(Ac\MaleFaceIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RN_StrFromInt$(Ac\FemaleFaceIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RN_StrFromInt$(Ac\MaleBodyIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RN_StrFromInt$(Ac\FemaleBodyIDs[i], 2) : Next
					For i = 0 To 15 : Pa$ = Pa$ + RN_StrFromInt$(Ac\MSpeechIDs[i], 2) : Next
					For i = 0 To 15 : Pa$ = Pa$ + RN_StrFromInt$(Ac\FSpeechIDs[i], 2) : Next
					Pa$ = Pa$ + RN_StrFromInt$(Ac\Rideable, 1) + RN_StrFromInt$(Ac\TradeMode, 1) + RN_StrFromInt$(Ac\BloodTexID, 2)
					Pa$ = Pa$ + RN_StrFromInt$(Ac\Aggressiveness, 1) + RN_StrFromInt$(Ac\Genders, 1)
					Pa$ = Pa$ + RN_StrFromInt$(Ac\Environment, 1) + RN_StrFromInt$(Ac\InventorySlots, 2)
					Pa$ = Pa$ + RN_StrFromInt$(Ac\MAnimationSet, 2) + RN_StrFromInt$(Ac\FAnimationSet, 2)
					Pa$ = Pa$ + RN_StrFromFloat$(Ac\Scale#)
					Pa$ = Pa$ + RN_StrFromInt$(Ac\DefaultFaction, 1)
					For i = 0 To 39
						Pa$ = Pa$ + RN_StrFromInt$(Ac\Attributes\Value[i], 2)
						Pa$ = Pa$ + RN_StrFromInt$(Ac\Attributes\Maximum[i], 2)
					Next
					Pa$ = Pa$ + RN_StrFromInt$(Len(Ac\Race$), 1) + Ac\Race$
					Pa$ = Pa$ + RN_StrFromInt$(Len(Ac\Class$), 1) + Ac\Class$
					Pa$ = Pa$ + RN_StrFromInt$(Len(Ac\Description$), 2) + Ac\Description$
					If ActorsSent >= 2 And Ac <> Last Actor
						SendQueued(Host, M\FromID, P_FetchActors, "N" + Pa$, True)
						ActorsSent = 0
						Pa$ = ""
					EndIf
				Next
				SendQueued(Host, M\FromID, P_FetchActors, "Y" + RN_StrFromInt$(TotalActors, 2) + Pa$, True)

			; New account creation request
			Case P_CreateAccount
				If AllowAccountCreation = True
					UsernameLen = RN_IntFromStr(Left$(M\MessageData$, 1))
					Username$ = Mid$(M\MessageData$, 2, UsernameLen)
					; Check that username does not already exist
					Exists = False
					If MySQL = True
						Exists = My_AccountExists(Username$)
					Else
						For A.Account = Each Account
							If Upper$(A\User$) = Upper$(Username$) Then Exists = True : Exit
						Next
					EndIf

					If Exists = False
						Offset = 2 + UsernameLen
						PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						Password$ = Mid$(M\MessageData$, Offset + 1, PwdLen)
						Offset = Offset + 1 + PwdLen
						EmailLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						Email$ = Encrypt$(Mid$(M\MessageData$, Offset + 1, EmailLen), 1)
						; Check that all characters are valid
						Valid = True
						For i = 1 To Len(Username$)
							Char = Asc(Mid$(Username$, i, 1))
							If Char < 32 Or Char > 122 Then Valid = False : Exit
						Next
						For i = 1 To Len(Email$)
							Char = Asc(Mid$(Email$, i, 1))
							If Char < 32 Or Char > 122 Then Valid = False : Exit
						Next
						If Len(Username$) > 50 Or Len(Password$) > 50 Or Len(Email$) > 200 Then Valid = False
						If Valid = True
							If MySQL = True
								My_AddAccount(Username$, Password$, Email$)
							Else
								AddAccount(Username$, Password$, Email$)
							EndIf
							RN_Send(Host, M\FromID, P_CreateAccount, "Y", True)
						Else
							RN_Send(Host, M\FromID, P_CreateAccount, "N", True)
						EndIf
					Else
						RN_Send(Host, M\FromID, P_CreateAccount, "N", True)
					EndIf
				EndIf

			; Account verification request
			Case P_VerifyAccount
				UsernameLen = RN_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)

				; MySQL version
				If MySQL = True
					; Get password
					Offset = 2 + UsernameLen
					PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
					Password$ = Mid$(M\MessageData$, Offset + 1, PwdLen)

					; Load account, don't force password skip
					A.Account = My_LoadAccount(Username$, Password$, False)

					If A = Null
						If My_Reason = MY_WRONGLOGIN
							RN_Send(Host, M\FromID, P_VerifyAccount, "P", True)
						EndIf
						If My_Reason = MY_BANNED
							RN_Send(Host, M\FromID, P_VerifyAccount, "B", True)
						EndIf
						If My_Reason = MY_NOACCOUNT
							RN_Send(Host, M\FromID, P_VerifyAccount, "N", True)
						EndIf
					Else	
						Pa$ = "Y"
						For i = 0 To 9
							If A\Character[i] <> Null
								Pa$ = Pa$ + RN_StrFromInt$(Len(A\Character[i]\Name$), 1) + A\Character[i]\Name$
								Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\Actor\ID, 2) + RN_StrFromInt$(A\Character[i]\Gender, 1)
								Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\FaceTex, 1) + RN_StrFromInt$(A\Character[i]\Hair, 1)
								Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\Beard, 1) + RN_StrFromInt$(A\Character[i]\BodyTex, 1)
							EndIf
						Next
						RN_Send(Host, M\FromID, P_VerifyAccount, Pa$, True)
					EndIf
				; Non-MySQL version
				Else
					; Find account
					Exists = False
					For A.Account = Each Account
						If Upper$(A\User$) = Upper$(Username$)
							Offset = 2 + UsernameLen
							PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
							; If password is correct, send back character list
							If A\Pass$ = Mid$(M\MessageData$, Offset + 1, PwdLen) And A\IsBanned = False
								Pa$ = "Y"
								For i = 0 To 9
									If A\Character[i] <> Null
										Pa$ = Pa$ + RN_StrFromInt$(Len(A\Character[i]\Name$), 1) + A\Character[i]\Name$
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\Actor\ID, 2) + RN_StrFromInt$(A\Character[i]\Gender, 1)
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\FaceTex, 1) + RN_StrFromInt$(A\Character[i]\Hair, 1)
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\Beard, 1) + RN_StrFromInt$(A\Character[i]\BodyTex, 1)
									EndIf
								Next
								RN_Send(Host, M\FromID, P_VerifyAccount, Pa$, True)
							; Otherwise return password failure
							Else
								If A\IsBanned = False
									RN_Send(Host, M\FromID, P_VerifyAccount, "P", True)
								Else
									RN_Send(Host, M\FromID, P_VerifyAccount, "B", True)
								EndIf
							EndIf
							Exists = True
							Exit
						EndIf
					Next
					; If account was not found, return failure
					If Exists = False Then RN_Send(Host, M\FromID, P_VerifyAccount, "N", True)
				EndIf

			; Change account password request
			Case P_ChangePassword
				UsernameLen = RN_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; If password is correct, change it to the new one
						If A\Pass$ = Mid$(M\MessageData$, Offset + 1, PwdLen)
							Offset = 2 + PwdLen
							PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
							A\Pass$ = Mid$(M\MessageData$, Offset + 1, PwdLen)
							RN_Send(Host, M\FromID, P_ChangePassword, "Y", True)
							If MySQL = True Then My_SaveAccount(A, False)
						; Otherwise return password failure
						Else
							RN_Send(Host, M\FromID, P_ChangePassword, "P", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next

				; If account was not found, return failure
				If Exists = False Then RN_Send(Host, M\FromID, P_ChangePassword, "N", True)

			; Request to fetch character data
			Case P_FetchCharacter
				UsernameLen = RN_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; If password is correct
						If A\Pass$ = Mid$(M\MessageData$, Offset + 1, PwdLen) And A\IsBanned = False
							Offset = Offset + 1 + PwdLen
							Number = Asc(Mid$(M\MessageData$, Offset, 1))
							If Number > -1 And Number < 10
								; Send character data
								Pa$ = RN_StrFromInt$(A\Character[Number]\Gold, 4) + RN_StrFromInt$(A\Character[Number]\Reputation, 2)
								Pa$ = Pa$ + RN_StrFromInt$(A\Character[Number]\Level, 2) + RN_StrFromInt$(A\Character[Number]\XP, 4)
								Pa$ = Pa$ + RN_StrFromInt$(A\Character[Number]\HomeFaction, 1)
								For i = 0 To 39
									Pa$ = Pa$ + RN_StrFromInt$(A\Character[Number]\Attributes\Value[i], 2)
									Pa$ = Pa$ + RN_StrFromInt$(A\Character[Number]\Attributes\Maximum[i], 2)
								Next
								SendQueued(Host, M\FromID, P_FetchCharacter, "C1" + Pa$, True)
								Pa$ = ""
								For i = 0 To 49
									If A\Character[Number]\Inventory\Items[i] <> Null
										Pa$ = Pa$ + Chr$(i) + ItemInstanceToString$(A\Character[Number]\Inventory\Items[i])
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[Number]\Inventory\Amounts[i], 2)
									Else
										Pa$ = Pa$ + Chr$(99)
									EndIf
									If Len(Pa$) > 999 - ItemInstanceStringLength()
										SendQueued(Host, M\FromID, P_FetchCharacter, "C3" + Pa$, True)
										Pa$ = ""
									EndIf
								Next
								If Pa$ <> "" Then SendQueued(Host, M\FromID, P_FetchCharacter, "C3" + Pa$, True)
								; Send known spells
								Pa$ = ""
								SpellsDone = 0
								For i = 0 To 999
									If A\Character[Number]\SpellLevels[i] > 0
										OldPa$ = Pa$
										Sp.Spell = SpellsList(A\Character[Number]\KnownSpells[i])
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[Number]\SpellLevels[i], 2) + RN_StrFromInt$(Sp\ID, 2)
										Pa$ = Pa$ + RN_StrFromInt$(Sp\ThumbnailTexID, 2) + RN_StrFromInt$(Sp\RechargeTime, 2)
										Pa$ = Pa$ + RN_StrFromInt$(Len(Sp\Name$), 2) + Sp\Name$ + RN_StrFromInt$(Len(Sp\Description$), 2) + Sp\Description$
										Pa$ = Pa$ + RN_StrFromInt$(0, 1)
										For j = 0 To 9
											If A\Character[Number]\MemorisedSpells[j] = i
												Pa$ = Left$(Pa$, Len(Pa$) - 1) + RN_StrFromInt$(1, 1)
												Exit
											EndIf
										Next
										SpellsDone = SpellsDone + 1
										If Len(Pa$) > 1000
											i = i - 1
											Pa$ = ""
											SendQueued(Host, M\FromID, P_FetchCharacter, "S" + OldPa$, True)
										EndIf
									EndIf
								Next
								If Pa$ <> "" Then SendQueued(Host, M\FromID, P_FetchCharacter, "S" + Pa$, True)
								; Send quest log
								Pa$ = "" : Num = 0
								For i = 0 To 499
									If A\QuestLog[Number]\EntryName$[i] <> ""
										Num = Num + 1
										Pa$ = Pa$ + RN_StrFromInt$(Len(A\QuestLog[Number]\EntryName$[i]), 1)
										Pa$ = Pa$ + A\QuestLog[Number]\EntryName$[i]
										Pa$ = Pa$ + RN_StrFromInt$(Len(A\QuestLog[Number]\EntryStatus$[i]), 2)
										Pa$ = Pa$ + A\QuestLog[Number]\EntryStatus$[i]
										If Len(Pa$) > 700
											SendQueued(Host, M\FromID, P_FetchCharacter, "Q" + Pa$, True)
											Pa$ = ""
										EndIf
									EndIf
								Next
								If Len(Pa$) > 0 Then SendQueued(Host, M\FromID, P_FetchCharacter, "Q" + Pa$, True)
								SendQueued(Host, M\FromID, P_FetchCharacter, "F" + RN_StrFromInt$(Num, 2) + RN_StrFromInt$(SpellsDone, 2), True)
							Else
						        RN_Send(Host, M\FromID, P_FetchCharacter, "N", True)
							EndIf
						; Otherwise return failure
						Else
							RN_Send(Host, M\FromID, P_FetchCharacter, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found, return failure
		        If Exists = False Then RN_Send(Host, M\FromID, P_FetchCharacter, "N", True)

			; New charater creation request
			Case P_CreateCharacter
				UsernameLen = RN_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; If password is correct
						If A\Pass$ = Mid$(M\MessageData$, Offset + 1, PwdLen)
							Offset = Offset + 1 + PwdLen

							; Check character name is valid
							NameValid = True
							Name$ = Upper$(Mid$(M\MessageData$, Offset + 47))
							F = ReadFile("Data\Server Data\Names Filter.txt")
							If F = 0 Then RuntimeError("Could not open Names Filter.txt!")
								While Eof(F) = False
									Banned$ = Trim$(ReadLine$(F))
									If Banned$ <> ""
										If Left$(Banned$, 1) <> ";"
											If Instr(Name$, Upper$(Banned$)) > 0 Then NameValid = False : Exit
										EndIf
									EndIf
								Wend
							CloseFile(F)

							; Check character name is not already in use
							For AI.ActorInstance = Each ActorInstance
								If AI\RNID >= 0
									If Upper$(AI\Name$) = Name$
										NameValid = False
										Exit
									EndIf
								EndIf
							Next

							; Name was valid
							If NameValid = True
								; Find free slot
								FreeSlot = -1
								TotalChars = 0
								For i = 0 To 9
									If A\Character[i] = Null
										If FreeSlot = -1 Then FreeSlot = i
									Else
										TotalChars = TotalChars + 1
									EndIf
								Next

								; If we have a free slot and haven't exceeded the maximum allowed characters
								If FreeSlot > -1 And TotalChars < MaxAccountChars
									ActorID = RN_IntFromStr(Mid$(M\MessageData$, Offset, 2))
									A\QuestLog[FreeSlot] = New QuestLog
									A\ActionBar[FreeSlot] = New ActionBarData
									A\Character[FreeSlot] = CreateActorInstance(ActorList(ActorID))
									A\Character[FreeSlot]\Account = Handle(A)
									C.ActorInstance = A\Character[FreeSlot]
									C\Gender = RN_IntFromStr(Mid$(M\MessageData$, Offset + 2, 1))
									C\FaceTex = RN_IntFromStr(Mid$(M\MessageData$, Offset + 3, 1))
									C\Hair = RN_IntFromStr(Mid$(M\MessageData$, Offset + 4, 1))
									C\Beard = RN_IntFromStr(Mid$(M\MessageData$, Offset + 5, 1))
									C\BodyTex = RN_IntFromStr(Mid$(M\MessageData$, Offset + 6, 1))
									C\Area$ = C\Actor\StartArea$
									C\Gold = StartGold
									C\Reputation = StartReputation
									Ar.Area = FindArea(C\Area$)
									If Ar = Null Then RuntimeError("Start zone for new character not found!")
									For i = 0 To 99
										If Upper$(Ar\PortalName$[i]) = Upper$(C\Actor\StartPortal$)
											C\LastPortal = i
											C\X# = Ar\PortalX#[i]
											C\Y# = Ar\PortalY#[i]
											C\Z# = Ar\PortalZ#[i]
											Exit
										EndIf
									Next
									Offset = Offset + 7
									If AttributeAssignment > 0
										TotalAmount = 0
										For i = 0 To 39
											Amount = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
											TotalAmount = TotalAmount + Amount
											C\Attributes\Value[i] = C\Attributes\Value[i] + Amount
											Offset = Offset + 1
										Next
										; Check for cheating
										If TotalAmount > AttributeAssignment
											FreeActorInstance(A\Character[FreeSlot])
									        RN_Send(Host, M\FromID, P_CreateCharacter, "N", True)
											Exists = True : Exit
										EndIf
									EndIf
									C\Name$ = Right$(M\MessageData$, Len(M\MessageData$) - (Offset - 1))
									; If MySQL is enabled, then save individual account (fast)
									If MySQL = True
										; Similar to old command, however, takes no file/stream argument
										My_NewActorInstance(C, A\QuestLog[FreeSlot], A\ActionBar[FreeSlot], False, A\My_ID)
									; Otherwise save all accounts
									Else
										SaveAccounts()
							        EndIf
							        RN_Send(Host, M\FromID, P_CreateCharacter, "Y", True)
								; If there are no free slots, reply with failure
								Else
							        RN_Send(Host, M\FromID, P_CreateCharacter, "N", True)
								EndIf
							; Character name invalid, tell client
							Else
						        RN_Send(Host, M\FromID, P_CreateCharacter, "I", True)
							EndIf
						; If password is incorrect, reply with failure
						Else
					        RN_Send(Host, M\FromID, P_CreateCharacter, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found, return failure
		        If Exists = False Then RN_Send(Host, M\FromID, P_CreateCharacter, "N", True)

			; Request to delete an existing character
			Case P_DeleteCharacter
				UsernameLen = RN_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RN_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; If password is correct
						If A\Pass$ = Mid$(M\MessageData$, Offset + 1, PwdLen)
							Offset = Offset + 1 + PwdLen
							Number = Asc(Mid$(M\MessageData$, Offset, 1))
							If Number > -1 And Number < 10
								; Delete the character
								If A\QuestLog[Number] <> Null Then Delete A\QuestLog[Number]
								If MySQL = True
									My_DeleteCharacter(A, Number)
								Else
									DeleteCharacter(A, Number)
								EndIf
								For i = Number To 8
									A\Character[i] = A\Character[i + 1]
									A\QuestLog[i] = A\QuestLog[i + 1]
									A\ActionBar[i] = A\ActionBar[i + 1]
								Next
								A\Character[9] = Null
								A\QuestLog[9] = Null
								A\ActionBar[9] = Null

								; Send back new character list
								Pa$ = ""
								For i = 0 To 9
									If A\Character[i] <> Null
										Pa$ = Pa$ + RN_StrFromInt$(Len(A\Character[i]\Name$), 1) + A\Character[i]\Name$
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\Actor\ID, 2)
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\Gender, 1)
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\FaceTex, 1) + RN_StrFromInt$(A\Character[i]\Hair, 1)
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\Beard, 1)
										Pa$ = Pa$ + RN_StrFromInt$(A\Character[i]\BodyTex, 1)
									EndIf
								Next
								RN_Send(Host, M\FromID, P_DeleteCharacter, Pa$, True)
							Else
						        RN_Send(Host, M\FromID, P_DeleteCharacter, "N", True)
							EndIf
						; Otherwise return failure
						Else
					        RN_Send(Host, M\FromID, P_DeleteCharacter, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found, return failure
		        If Exists = False Then RN_Send(Host, M\FromID, P_DeleteCharacter, "N", True)

		End Select
		Delete M
	Next

End Function

; Updates other players in area on what an actor is wearing
Dim ActivateGubbins(5)
Function SendEquippedUpdate(A.ActorInstance)

	; Call script
	RunScript("Equip Change", "Main", A, Null)

	; Create packet with item IDs
	Pa$ = "O" + RN_StrFromInt$(A\RuntimeID, 2)
	If A\Inventory\Items[SlotI_Weapon] <> Null
		Pa$ = Pa$ + RN_StrFromInt$(A\Inventory\Items[SlotI_Weapon]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RN_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Shield] <> Null
		Pa$ = Pa$ + RN_StrFromInt$(A\Inventory\Items[SlotI_Shield]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RN_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Chest] <> Null
		Pa$ = Pa$ + RN_StrFromInt$(A\Inventory\Items[SlotI_Chest]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RN_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Hat] <> Null
		Pa$ = Pa$ + RN_StrFromInt$(A\Inventory\Items[SlotI_Hat]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RN_StrFromInt$(65535, 2)
	EndIf

	; Find out which gubbins to activate
	For i = 0 To 5 : ActivateGubbins(i) = False : Next
	For i = 0 To SlotI_Backpack - 1
		If A\Inventory\Items[i] <> Null
			For j = 0 To 5
				If A\Inventory\Items[i]\Item\Gubbins[j] = True Then ActivateGubbins(j) = True
			Next
		EndIf
	Next
	For i = 0 To 5
		Pa$ = Pa$ + RN_StrFromInt$(ActivateGubbins(i), 1)
	Next

	; Send to all players in the same area
	AInstance.AreaInstance = Object.AreaInstance(A\ServerArea)
	A2.ActorInstance = AInstance\FirstInZone
	While A2 <> Null
		If A2\RNID > 0
			If A2 <> A Then RN_Send(Host, A2\RNID, P_InventoryUpdate, Pa$, True)
		EndIf
		A2 = A2\NextInZone
	Wend

End Function

; Updates a player on who is in his party
Function SendPartyUpdate(AI.ActorInstance)

	P.Party = Object.Party(AI\PartyID)
	If P <> Null
		For i = 0 To 7
			If P\Player[i] <> Null
				Pa$ = ""
				For j = 0 To 7
					If P\Player[j] <> Null And j <> i Then Pa$ = Pa$ + RN_StrFromInt$(Len(P\Player[j]\Name$), 1) + P\Player[j]\Name$
				Next
				RN_Send(Host, P\Player[i]\RNID, P_PartyUpdate, Pa$, True)
			EndIf
		Next
	EndIf

End Function