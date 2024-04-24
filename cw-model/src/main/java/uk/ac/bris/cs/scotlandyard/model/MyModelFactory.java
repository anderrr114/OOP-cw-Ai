package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static uk.ac.bris.cs.scotlandyard.model.Model.Observer.Event.GAME_OVER;
import static uk.ac.bris.cs.scotlandyard.model.Model.Observer.Event.MOVE_MADE;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	@Nonnull @Override public Model build(GameSetup setup,
	                                      Player mrX,
	                                      ImmutableList<Player> detectives) {



		Model model = new Model() {
			final MyGameStateFactory createState = new MyGameStateFactory();
			Board.GameState newState = createState.build(setup, mrX, detectives);
			final ArrayList<Observer> observers = new ArrayList<>();

			@Nonnull
			@Override
			public Board getCurrentBoard() {
				return newState;
			}

			@Override
			public void registerObserver(@Nonnull Observer observer) {
				if (observer == null) throw new NullPointerException();
				else if (observers.contains(observer)) throw new IllegalArgumentException();

				observers.add(observer);
			}

			@Override
			public void unregisterObserver(@Nonnull Observer observer) {
				if (observer == null) throw new NullPointerException();
				else if (!observers.contains(observer)) throw new IllegalArgumentException();
				observers.remove(observer);
			}

			@Nonnull
			@Override
			public ImmutableSet<Observer> getObservers() {
				return ImmutableSet.copyOf(observers);
			}

			@Override
			public void chooseMove(@Nonnull Move move) {
				Observer.Event event;

				if (newState.getAvailableMoves().isEmpty()) event = GAME_OVER;
				else event = MOVE_MADE;

				if (event == MOVE_MADE) newState = newState.advance(move);

				if(!newState.getWinner().isEmpty()) event = GAME_OVER;

				// why need to store event in another final event?
				Observer.Event finalEvent = event;
				observers.forEach(Player -> Player.onModelChanged(getCurrentBoard(), finalEvent));

			}
		};




		return model;
	}
}
