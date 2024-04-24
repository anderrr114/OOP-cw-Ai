package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.*;

import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {
	private static final class MyGameState implements GameState {
		private final GameSetup setup;
		private final ImmutableSet<Piece> remaining;
		private final ImmutableList<LogEntry> log;
		private final Player mrX;
		private final List<Player> detectives;
		private final ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;

		private MyGameState(final GameSetup setup,
							final ImmutableSet<Piece> remaining,
							final ImmutableList<LogEntry> log,
							final Player mrX,
							final List<Player> detectives){
			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;
			this.moves = getAvailableMoves();

			// this set used for check Duplicate Detectives
			Set<Piece> playerSet = new HashSet<>();
			Set<Integer> locationSet = new HashSet<>();

			// check Mrx and detectives are not null
			if(mrX == null || detectives == null) throw new NullPointerException();
			// check Mrx doesn't exist
			if(mrX.piece() != MRX) throw new IllegalArgumentException();

			// get player colour into player set
			for(Player i : detectives){
				playerSet.add(i.piece());
				if (i.piece() == MRX) throw new IllegalArgumentException();
			}
			if(playerSet.size() != detectives.size()) throw new IllegalArgumentException();

			// get location into location set
			for(Player i : detectives){
				locationSet.add(i.location());
			}
			if(locationSet.size() != detectives.size()) throw new IllegalArgumentException();


			// to test Detective have Secret Ticket and Double Ticket or not
			for (Player i : detectives){
				if(i.has(ScotlandYard.Ticket.SECRET)) throw new IllegalArgumentException();
				if(i.has(ScotlandYard.Ticket.DOUBLE)) throw new IllegalArgumentException();
			}

			// test Empty Moves and empty graph
			if (setup.moves.isEmpty() || setup.graph.nodes().isEmpty()) throw new IllegalArgumentException();
		}

		@Nonnull
		@Override
		public GameState advance(Move move) {
			// check if the move is available
			if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);
			else{

				// destination of the player after move
				ArrayList<Integer> destination = move.accept(new Move.Visitor<>(){
					@Override public ArrayList<Integer> visit(Move.SingleMove singleMove){
						return new ArrayList<>(Arrays.asList(singleMove.destination)); }
					@Override public ArrayList<Integer> visit(Move.DoubleMove doubleMove){
						return new ArrayList<>(Arrays.asList(doubleMove.destination1, doubleMove.destination2)); }
				});


				// get the user(Player of the move)
				Piece user = move.commencedBy();

				// for Mrx
				if (user.isMrX()) {
					Player tempMrx = this.mrX;					// create tem Mrx for return value
					LogEntry newLog;							// create a new log will add into the log
					ArrayList<LogEntry> tempArr = new ArrayList<>(log);  // total log record

					// if double move
					if (destination.size() > 1){
						//  get log
						for(int i = 0; i <= 1; i++){
							if (setup.moves.get(tempArr.size()))
								newLog = LogEntry.reveal(move.tickets().iterator().next(), destination.get(i));
							else newLog = LogEntry.hidden(move.tickets().iterator().next());
							tempArr.add(newLog);
						}
						tempMrx = tempMrx.at(destination.get(1));
					} // if single move
					else{
						//  get log
						if (setup.moves.get(tempArr.size()))
							newLog = LogEntry.reveal(move.tickets().iterator().next(), destination.get(0));
						else newLog = LogEntry.hidden(move.tickets().iterator().next());
						tempArr.add(newLog);
						tempMrx = tempMrx.at(destination.get(0));
					}
					tempMrx = tempMrx.use(move.tickets());

					// get remaining
					ArrayList<Piece> newRemaining = new ArrayList<>();
					for (Player i : detectives){
						if (!(i.tickets().get(ScotlandYard.Ticket.BUS) == 0 &&
								i.tickets().get(ScotlandYard.Ticket.TAXI) == 0 &&
								i.tickets().get(ScotlandYard.Ticket.UNDERGROUND) == 0))
							newRemaining.add(i.piece());
					}
					// update the game with Mister X use ticket
					return new MyGameState(
							this.setup,
							ImmutableSet.copyOf(newRemaining),
							ImmutableList.copyOf(tempArr),
							tempMrx,
							this.detectives);
				} // for detective
				else {
					int detectIndex = 0;					// location of Player
					Player newDetect = null;				// new Detective
					for (Player i : detectives){
						if (i.piece() == user) {
							newDetect = i.use(move.tickets());
							mrX.give(move.tickets());
							break;
						}
						detectIndex++;
					}
					List<Player> newDetectives = new ArrayList<>(detectives);
					newDetect = newDetect.at(destination.get(0));
					newDetectives.set(detectIndex, newDetect);

					// get remaining
                    ArrayList<Piece> newRemaining = new ArrayList<>(remaining);
					newRemaining.remove(newDetect.piece());
					if (newRemaining.isEmpty() && log.size() < setup.moves.size()) newRemaining.add(mrX.piece());

					Player newMrX = mrX.give(move.tickets());

					return new MyGameState(
							this.setup,
							ImmutableSet.copyOf(newRemaining),
							this.log,
							newMrX,
							newDetectives);
				}
			}
		}

		@Nonnull
		@Override
		public GameSetup getSetup() { return setup; }

		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			// get the set for future copy to ImmutableSet
			Set<Piece> part = new HashSet<>();

			part.add(mrX.piece());
			for (Player i : detectives){
				part.add(i.piece());
			}
			// get the result list of correct type
			System.out.println(part);
            return ImmutableSet.copyOf(part);
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			for (Player i : detectives){
				if (i.piece() == detective) return Optional.of(i.location());
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			TicketBoard totalTicket = new TicketBoard() {
				@Override
				public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
					for (Player i : detectives) {
						if (i.piece() == piece) return i.tickets().get(ticket);
					}
					if (piece.isMrX()) return mrX.tickets().get(ticket);
					return -1;
				}
			};
			if (totalTicket.getCount(ScotlandYard.Ticket.TAXI) == -1) return Optional.empty();
			return Optional.of(totalTicket);
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() { return log; }

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			ArrayList<Piece> winDetectives = new ArrayList<>();
			for (Player i : detectives) winDetectives.add(i.piece());
			boolean detectivesWin = false;

			// mr x fill all log
			if (remaining.isEmpty()) return ImmutableSet.of(MRX);

			// detectives can't move anymore
			int totalDetectives = 0;
			for (Player i : detectives) {
				if ((i.tickets().get(ScotlandYard.Ticket.BUS) == 0 &&
						i.tickets().get(ScotlandYard.Ticket.TAXI) == 0 &&
						i.tickets().get(ScotlandYard.Ticket.UNDERGROUND) == 0)) {
					totalDetectives++;
				}
			}
			if (totalDetectives == detectives.size()) return ImmutableSet.of(MRX);

			// detectives catch mr x
			for (Player i : detectives) {
				if (i.location() == mrX.location()) {
					detectivesWin = true;
					break;
				}
			}

			// Mr x can't move to unoccupied stations
			if(moves != null && moves.isEmpty()) detectivesWin = true;

			if (detectivesWin) { return ImmutableSet.copyOf(winDetectives); }
			return ImmutableSet.of();
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			if (!getWinner().isEmpty()) return ImmutableSet.of();

			// Use a builder to return the moves created by makeSingleMoves
			ImmutableSet.Builder<Move> builder = ImmutableSet.builder();
			// List detectives don't contain mrx
			// so i creat a new list that contains Mr.x and detectives
			List <Player> allPlayer = new ArrayList<>(detectives);
			// doubleCheck if Mr.x exist
			if (remaining.contains(MRX)) {
				int mrxLocation = mrX.location();
				//generate first move
				Set<Move.SingleMove> moves = makeSingleMoves(setup, allPlayer, mrX, mrxLocation);
				builder.addAll(moves);
				//generate second move
				if (mrX.hasAtLeast(ScotlandYard.Ticket.DOUBLE,1) && log.size() + 1 != setup.moves.size()) {
					Set<Move.DoubleMove> moves1 = makeDoubleMoves(setup,allPlayer,mrX,mrxLocation);
					builder.addAll(moves1);
				}
				// return the moves of Mr.x :)
				return builder.build();
			}
			// for detectives
			for (Player i : allPlayer) {
				if (remaining.contains(i.piece())) {
					int source = i.location();
					Set<Move.SingleMove> moves = makeSingleMoves(setup, detectives, i, source);
					builder.addAll(moves);
				}
			}
            return builder.build();
		}

	};

	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(MRX), ImmutableList.of(), mrX, detectives);
	}
	private static Set<Move.SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source){
		// TODO create an empty collection of some sort, say, HashSet, to store all the SingleMove we generate
		Set<Move.SingleMove> total = new HashSet<>();
		Set<Integer> adjecentNode = setup.graph.adjacentNodes(source);
		boolean IsthereAnyPlayer = false;
		// TODO find out if destination is occupied by a detective
		for (int node : adjecentNode){
			for (Player i : detectives)
			{
                if (i.location() == node) {
                    IsthereAnyPlayer = true;
                    break;
                }
			}
				if (!IsthereAnyPlayer)
				{
					for (ScotlandYard.Transport transport : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, node, ImmutableSet.of())))
					{
						// TODO find out if the player has the required tickets
						if (player.has(transport.requiredTicket()) )
						{
							Move.SingleMove singleMove = new Move.SingleMove(player.piece(), source, transport.requiredTicket(), node);
							total.add(singleMove);
						}
						// TODO consider the rules of secret moves here
						if (player.has(ScotlandYard.Ticket.SECRET) )
						{
							total.add(new Move.SingleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, node));
						}
					}
				}
			IsthereAnyPlayer = false;
		}
		// TODO return the collection of moves
		return total;
		// Detectives and Mr.x can make single move now :)
    }
	private static Set<Move.DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
		// Create a new Set to hold the new possible double moves
		Set<Move.DoubleMove> doubleMoves = new HashSet<>();
		//firstly call all the available first move by call the makeSingleMoves
		Set<Move.SingleMove> firstMoves = makeSingleMoves(setup, detectives, player, source);
		// read the destination after first move
		for (Move.SingleMove afterFirst : firstMoves)
		{
			// get the destination of first move
			int firstDestination = afterFirst.destination;
				// make the second move
				Set<Move.SingleMove> secondMoves = makeSingleMoves(setup, detectives, player, firstDestination);
				for (Move.SingleMove aftersecond : secondMoves)
				{
					//Store the location of second move as a Int
					int secondDestination = aftersecond.destination;
					// check detective or Mr.x have enough ticket  to do double move and check whether the round is enough
					if (aftersecond.ticket != afterFirst.ticket &&(player.hasAtLeast(aftersecond.ticket,1) && player.hasAtLeast(afterFirst.ticket,1))|| player.hasAtLeast(afterFirst.ticket,2))
					{
						// add the moves to the set
						doubleMoves.add(new Move.DoubleMove(player.piece(),source,afterFirst.ticket,firstDestination,aftersecond.ticket,secondDestination));
					}
				}
		}
		// return the set
		return doubleMoves;
	}
}
