package uk.ac.bris.cs.scotlandyard.ui.ai;
import com.google.common.collect.ImmutableMap;
import javafx.scene.shape.MoveTo;
import org.w3c.dom.Node;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.swing.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import com.google.common.graph.ValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;

public class MyAi implements Ai {
	private  GameSetup setup = null;
	private ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> gameMap;
	private  Board.GameState gameState;
	private  Player mrX;
	private  ImmutableList<Player> detectives;
	private  Set<Integer> CurrentPosition = new HashSet<>();			// detectives current position
	private  int GameCount = 1;					// count round of the game


    @Nonnull @Override public String name() { return "R2D2"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		//Creat a new Game State if game just start
		if (GameCount == 1) newGamestate(board);

		CurrentPosition = new HashSet<>();			// get positions from game state
		for (Piece p : board.getPlayers())
		{
			if (p.isDetective())
			{
				CurrentPosition.add(board.getDetectiveLocation((Detective) p).get());
			}
		}
//		System.out.println(CurrentPosition);
		var availableMoves = board.getAvailableMoves();
//		System.out.println(availableMoves.size());
//		System.out.println(availableMoves);

		// create a map
		MutableValueGraph<Integer,Integer> safemap = CreateMap();

		// update the map
		safemap = updateMap(safemap);

		/*
		* check that Mr X will not be catch in next 2 rounds, which call it "safe"
		* in that situation, Mr X will only use single move without using any special tickets (double, secret)
		*/
		List<Move> singleMove = new ArrayList<>();
		for (Move move : availableMoves)
		{
			boolean IsSecret = false;				// can Mr X be seen
			for (ScotlandYard.Ticket ticket:move.tickets())
			{
                if (ticket == ScotlandYard.Ticket.SECRET)
				{
                    IsSecret = true;
                    break;
                }
			}
			if (IsSecret)
			{
				continue;
			}
			if (move instanceof Move.SingleMove)
			{
				singleMove.add(move);
			}
		}

		// Mr X can be catch in 2 rounds
		boolean Two = IsDetectiveNearby(CurrentPosition);
//		System.out.println(Two);
		var bestMove = singleMove.get(new Random().nextInt(singleMove.size()));
		boolean PositiondetectiveCannotReach = CheckFutrueweight(availableMoves , safemap);
		System.out.println("Can't Reach" +PositiondetectiveCannotReach);
		// dijkstra algorithm applied
		int [] Score = updatedijkstra(safemap);
		if (Two && PositiondetectiveCannotReach)
		{
			System.out.println("Detective Nearby but can find way out");
			bestMove = ChoosePositionwhiledetectiveCannotReach(availableMoves , safemap);			// find 0
		}else if (Two){
			System.out.println("Detective Can reach Mrx within 2 rounds ");
			bestMove = ChooseMoveWhileDetectiveNearBy(availableMoves,Score);						// find best weight
		}
//		System.out.println(bestMove);
		GameCount++;
		return bestMove;
	}

	// arrived by 2 rounds
	public boolean IsDetectiveNearby (Set<Integer> CurrentPosition)
	{
		// To justify whether detective will arrive in next 2 round
		boolean Nearby = false;
		int MrxLocation = mrX.location();
		for (Integer node : CurrentPosition)
		{
			for (Integer node2 : gameMap.adjacentNodes(node))
			{
				if (node2 == MrxLocation)
				{
					Nearby = true;
					return Nearby;
				}
				for (Integer node3 : gameMap.adjacentNodes(node2))
				{
					if (node3 == MrxLocation)
					{
						Nearby = true;
						return Nearby;
					}
				}
			}
		}
		return Nearby;
	}

	// (if exist) 0 with both single and double moves, return boolean
	public boolean CheckFutrueweight(ImmutableSet<Move> availablemove,MutableValueGraph<Integer,Integer> safemap)
	{
		boolean exist = false;
		int mrXLocation = mrX.location();
		for (Move move: availablemove)
		{

			if (move instanceof Move.SingleMove) {
				int target = destination1(move);
				int weight = safemap.edgeValueOrDefault(mrXLocation, target, 99);
				if (weight == 0)
				{
					exist = true;
					return exist;
				}
			} else if (move instanceof Move.DoubleMove) {
				int firstTarget = destination1(move);
				int secondTarget =destination(move);
				int weightSecond = safemap.edgeValueOrDefault(firstTarget, secondTarget, 99);

				if(weightSecond == 0 && firstTarget != secondTarget)
				{
					exist = true;
					return exist;
				}
			}
		}
		return exist;
	}

	/*
	* find the exist moves by following order:
	* Single
	* Double
	* Secret
	*
	* return that Move (anyone)
	*/
	public Move ChoosePositionwhiledetectiveCannotReach(ImmutableSet<Move> availablemove,MutableValueGraph<Integer,Integer> safemap)
	{
		// this method is to found out is there any weight that detective can't reach in next 2 round
		// and try to avoid waste the double move ticket.
		var bestmove = availablemove.asList().get(0);
		List<Move> Secret = new ArrayList<>();
		int mrXLocation = mrX.location();
		for (Move move :availablemove) {
			//added the moves that used secret ticket to another set and found out it if we can't use regular ticket
			boolean usedSecret = false;
			for (ScotlandYard.Ticket t : move.tickets())
			{
				if (t == ScotlandYard.Ticket.SECRET)
				{
					usedSecret = true;
				}
			}
			if (move instanceof Move.SingleMove && !usedSecret) {
				int target = destination1(move);
				int weight = safemap.edgeValueOrDefault(mrXLocation, target, 99);
				if (weight == 0)
				{
					bestmove = move;
					return bestmove;
				}
			} else if (move instanceof Move.DoubleMove && !usedSecret) {
				int firstTarget = destination1(move);
				int secondTarget = destination(move);
				int weightSecond = safemap.edgeValueOrDefault(firstTarget, secondTarget, 99);
				if(weightSecond == 0 && firstTarget != secondTarget)
				{
					bestmove = move;
					return  bestmove;
				}
			}else if (move instanceof Move.SingleMove)
			{
				int target = destination1(move);
				int weight = safemap.edgeValueOrDefault(mrXLocation, target, 99);
				if (weight == 0)
				{
					Secret.add(move);
				}
			}else
			{
				int firstTarget = destination1(move);
				int secondTarget = destination(move);
				int weightSecond = safemap.edgeValueOrDefault(firstTarget, secondTarget, 99);
				if(weightSecond == 0 && firstTarget != secondTarget)
				{
					Secret.add(move);
				}
			}
		}
		bestmove = Secret.get(new Random().nextInt(Secret.size()));
		return bestmove;
	}

	/*
	* no 0, find best move (return Move)
	* secret only use for ship
	*/
	public Move ChooseMoveWhileDetectiveNearBy(ImmutableSet<Move> availablemove,int [] dijkstra)
	{
		int count = 0;
		int largest = 0;
		int largestcount = 0;
		int mrXLocation = mrX.location();
		List<Move> regularMove = new ArrayList<>();
		List<Move> SecretMove = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		List<Integer> Secretweights = new ArrayList<>();
		for (Move move :availablemove)
		{
			//added the moves that used secret ticket to another set and found out it if we can't use regular ticket
			boolean usedSecret = false;
			for (ScotlandYard.Ticket t : move.tickets())
			{
				if (t == ScotlandYard.Ticket.SECRET)
				{
					System.out.println("Secret detected" + move);
					usedSecret = true;
				}
			}
			if (move instanceof  Move.SingleMove && !usedSecret)
			{
				int target = destination(move);
				int weight = dijkstra[target-1];
				System.out.println("node in " +target+ "weight is " + weight);
				weights.add(weight);
				regularMove.add(move);
			}else if (move instanceof Move.DoubleMove && !usedSecret)
			{
				int secondTarget = destination(move);
				int weight = dijkstra[secondTarget-1];
				if (mrXLocation == secondTarget) weight = dijkstra[mrXLocation];
				System.out.println("node in " +secondTarget+ "weight is " + weight);
				weights.add(weight);
				regularMove.add(move);
			}else if (move instanceof Move.SingleMove)
			{
				int target = destination(move);
				int weight = dijkstra[target-1];
				System.out.println("node in " +target+ "weight is " + weight);
				Secretweights.add(weight);
				SecretMove.add(move);
			}else
			{
				int Target = destination(move);
				int weight = dijkstra[Target-1];
				System.out.println("node in " +Target+ "weight is " + weight);
				if (mrXLocation == Target) weight = dijkstra[mrXLocation];
				Secretweights.add(weight);
				SecretMove.add(move);
			}
		}
		boolean useSecret = false;
		if (weights.isEmpty())
		// if the weight is empty, it means that either the regular ticket is run out or it reached a certain point that
		// could only access by using Secret Ticket(which is rarely seen)
		{
			useSecret =true;
			System.out.println("weights empty");
			for (Integer i : Secretweights)
			{
				count++;
				System.out.println(count);
				if (i >= largest && i < 99)
				{
					largestcount = count;
					largest = i;
				}
			}
		}else
		{
			for (Integer i : weights)
			// if we are not gonna to use the secret ticket, then find the largest weight from the set,
			// the philosophy is to find the largest weight and make the detective to cost more to get Mrx.
			{
				count++;
				System.out.println(count);
				if (i >= largest && i < 99)
				{
					largestcount = count;
					largest = i;
				}
			}
		}
		var move = availablemove.asList().get(0);
		if (!useSecret)
		{
			System.out.println(largest);
			move = regularMove.get(largestcount-1);
			System.out.println("found the best move" + move +" the weight is: " + largest);
			return move;
		}else {
			System.out.println(largest);
			move = SecretMove.get(largestcount-1);
			System.out.println("found the best Secret move" + move +" the weight is: " + largest);
			return move;
		}
    }
	public int destination(Move move)
	{

		return move.accept(new Move.Visitor<Integer>() {
			@Override
			public Integer visit(Move.SingleMove move) {
				return move.destination;
			}
			@Override
			public Integer visit(Move.DoubleMove move) {
				return move.destination2;
			}
		});
	}
	public int destination1 (Move move)
	{
		return move.accept(new Move.Visitor<Integer>() {
			@Override
			public Integer visit(Move.SingleMove move) {return move.destination;}
			@Override
			public Integer visit(Move.DoubleMove move) {return move.destination1;}
		});
	}
	public Board.GameState newGamestate( Board board)
		//Create a new Game state
	{
		// we can't directly get detectives from the board, therefore we must creat a new player from the ai part.
		List<Player> alldetectives = new ArrayList<>();
		for (Piece p : board.getPlayers())
		{
			// due to the order of the Immutable list is not the same,therefore I used a toString and using a switch loop
			// to justify the detectives and Mrx
			switch (p.toString())
			{
				case "RED":
					Player RED = new Player(p,ScotlandYard.defaultDetectiveTickets(),board.getDetectiveLocation((Detective) p).get());
					alldetectives.add(RED);
					break;
				case "GREEN" :
					Player GREEN = new Player(p,ScotlandYard.defaultDetectiveTickets(),board.getDetectiveLocation((Detective) p).get());
					alldetectives.add(GREEN);
					break;
				case "BLUE" :
					Player BLUE = new Player(p,ScotlandYard.defaultDetectiveTickets(),board.getDetectiveLocation((Detective) p).get());
					alldetectives.add(BLUE);
					break;
				case "WHITE" :
					Player WHITE = new Player(p,ScotlandYard.defaultDetectiveTickets(),board.getDetectiveLocation((Detective) p).get());
					alldetectives.add(WHITE);
					break;
				case "YELLOW":
					Player YELLOW = new Player(p,ScotlandYard.defaultDetectiveTickets(),board.getDetectiveLocation((Detective) p).get());
					alldetectives.add(YELLOW);
					break;
				case "MRX" :
					mrX = new Player(p,ScotlandYard.defaultMrXTickets(), MRXLocation(board));
					break;
			}
		}
		detectives = ImmutableList.copyOf(alldetectives);
		setup = board.getSetup();
		gameState = new MyGameStateFactory().build(setup,mrX,detectives);
		gameMap = board.getSetup().graph;
		return gameState;
	}
  	public int MRXLocation(Board board)
	{
		// we can't directly get the location of Mrx , Hence we can only get the location through the available moves
		int Location = 0;
		ImmutableSet<Integer> PossiblePosition = ImmutableSet.of(35, 45, 51, 71, 78, 104, 106, 127, 132, 166, 170, 172);
		for (Move move :board.getAvailableMoves())
		{
			int source = move.source();
			if (PossiblePosition.contains(source))
			{
				Location = source;
			}
		}
		return Location;
	}
	public MutableValueGraph<Integer, Integer> CreateMap() {
		MutableValueGraph<Integer, Integer> valueGraph = ValueGraphBuilder.undirected().build();
		for (Integer node : gameMap.nodes()) {
			valueGraph.addNode(node);
			for (Integer neighbor : gameMap.adjacentNodes(node)) {
				int weight = 0;
				// initial weight is 1
				valueGraph.putEdgeValue(node, neighbor, weight);
			}
		}

		return valueGraph;
	}
	public int [] updatedijkstra(MutableValueGraph<Integer, Integer> valueGraph)
	{
		int size = valueGraph.nodes().size();
		int[] distance = new int[size + 1]; // the node is from 1
		Arrays.fill(distance, 99);
		for (Integer node : CurrentPosition)
		{
			int[] dijsktra = dijkstra(valueGraph, node);
			// go through all the nodes and add the weight to the graph.
			for (Integer round1 : valueGraph.adjacentNodes(node))
			{
				int dijkstraWeight = dijsktra[round1];
//				// get the original weight from the graph that already generate
				int originalWeight = distance[round1];
//				// if the weight calculated by the dijkstra is smaller, then replace it with it.
				if (dijkstraWeight < originalWeight)
				{
					System.out.println("weight in "+ round1 + " is : " + dijkstraWeight);
					distance[round1] = dijkstraWeight;
				}
				for (Integer round2 : valueGraph.adjacentNodes(round1))
				{
					if (Objects.equals(round2, node))
					{
						continue;
					}
					// use dijkstra to calculate the weight.
					int dijkstraWeight2 = dijsktra[round2];
					int originalWeight2 = distance[round2];
					// if the weight calculated by the dijkstra is smaller, then replace it with it.
					if (dijkstraWeight2 < originalWeight2)
					{
						System.out.println("weight in : " + round2 + " is : " + dijkstraWeight2);
						distance[round2] = dijkstraWeight2;
					}
				}
			}
		}
		return distance;
	}
	public MutableValueGraph<Integer, Integer> updateMap(MutableValueGraph<Integer, Integer> valueGraph) {
		// update the score nearby the detective in next 2 round
		final int TAXI = 3;
		final int BUS = 5;
		final int UNDERGROUND = 8;
		for (Integer node : CurrentPosition)
		{
			for (Integer round1 : valueGraph.adjacentNodes(node))
			{
				if(round1.equals(node)) continue;
				int weight = TAXI;
				ImmutableSet<ScotlandYard.Transport> transports = gameMap.edgeValue(node, round1).orElse(ImmutableSet.of());
				if (transports.contains(ScotlandYard.Transport.UNDERGROUND))
				{
					weight = UNDERGROUND;
					System.out.println("safemap node to round1 from: "+ node +" to "+ round1 +" weight is : "+ weight);
					valueGraph.putEdgeValue(node, round1, weight);
				}else if (transports.contains(ScotlandYard.Transport.BUS))
				{
					weight = BUS;
					System.out.println("safemap node to round1 from: "+ node +" to "+ round1 +" weight is : "+ weight);
					valueGraph.putEdgeValue(node , round1, weight);
				}else
				{
					System.out.println("safemap node to round1 from: "+ node +" to "+ round1 +" weight is : "+ weight);
					valueGraph.putEdgeValue(node,round1,weight);
				}
				for (Integer round2 : valueGraph.adjacentNodes(round1))
				{
					if (round2.equals(round1)) continue;
					int weightround2 = TAXI;
					ImmutableSet<ScotlandYard.Transport> transportsround2 = gameMap.edgeValue(round1, round2).orElse(ImmutableSet.of());
					if (transportsround2.contains(ScotlandYard.Transport.UNDERGROUND))
					{
						weightround2 = UNDERGROUND;
						System.out.println("safemap round1 to round2 from: "+ round1 +" to "+ round2 +" weight is : "+ weightround2);
						valueGraph.putEdgeValue(round1,round2,weightround2);
					}else if (transportsround2.contains(ScotlandYard.Transport.BUS))
					{
						weightround2 = BUS;
						System.out.println("safemap round1 to round2 from: "+ round1 +" to "+ round2 +" weight is : "+ weightround2);
						valueGraph.putEdgeValue(round1,round2,weightround2);
					}else
					{
						System.out.println("safemap round1 to round2 from: "+ round1 +" to "+ round2 +" weight is : "+ weightround2);
						valueGraph.putEdgeValue(round1,round2,weightround2);
					}
				}
			}
		}
		return valueGraph;
	}
	public int[] dijkstra(MutableValueGraph<Integer, Integer> graph, int source) {
		int size = graph.nodes().size();
		int[] distance = new int[size + 1]; // the node is from 1
		boolean[] visited = new boolean[size + 1];
		Arrays.fill(distance, 99);
		distance[source] = 0;

		PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingInt(node -> distance[node]));
		pq.offer(source);

		while (!pq.isEmpty()) {
			int current = pq.poll();
			if (visited[current]) continue;
			visited[current] = true;
			for (Integer neighbor : graph.adjacentNodes(current)) {
				// get the neighbor weight from the value graph
				if (visited[neighbor]) continue;
				int weight = graph.edgeValueOrDefault(current, neighbor, 99);
				int newDist = distance[current] + weight;
				if (newDist < distance[neighbor]) {
					distance[neighbor] = newDist;
					if (!pq.contains(neighbor)) {
						pq.remove(neighbor);
						pq.add(neighbor);
					}
				}
			}
		}
		return distance;
	}
}
