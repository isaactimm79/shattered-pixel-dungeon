/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.watabou.utils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Comparator;


public class PathFinder {
	
	public static int[] distance;
	private static int[] maxVal;
	
	private static boolean[] goals;
	private static int[] queue;
	private static boolean[] queued; //currently only used in getStepBack, other can piggyback on distance
	
	private static int size = 0;
	private static int width = 0;

	// A* working buffers
	private static int[] gScore;
	private static int[] fScore;
	private static int[] cameFrom;
	private static boolean[] closed;


		private static int[] dir;
	private static int[] dirLR;
	private static boolean[] diagLR;


	//performance-light shortcuts for some common pathfinder cases
	//they are in array-access order for increased memory performance
	public static int[] NEIGHBOURS4;
	public static int[] NEIGHBOURS8;
	public static int[] NEIGHBOURS9;

	//similar to their equivalent neighbour arrays, but the order is clockwise.
	//Useful for some logic functions, but is slower due to lack of array-access order.
	public static int[] CIRCLE4;
	public static int[] CIRCLE8;
	
	public static void setMapSize( int width, int height ) {
		
		PathFinder.width = width;
		PathFinder.size = width * height;
		
		distance = new int[size];
		goals = new boolean[size];
		queue = new int[size];
		queued = new boolean[size];

		maxVal = new int[size];
		Arrays.fill(maxVal, Integer.MAX_VALUE);

		dir = new int[]{-1, +1, -width, +width, -width-1, -width+1, +width-1, +width+1};
		dirLR = new int[]{-1-width, -1, -1+width, -width, +width, +1-width, +1, +1+width};
		// indices 0,2,5,7 are diagonals in dirLR
		diagLR = new boolean[]{true, false, true, false, false, true, false, true};


		NEIGHBOURS4 = new int[]{-width, -1, +1, +width};
		NEIGHBOURS8 = new int[]{-width-1, -width, -width+1, -1, +1, +width-1, +width, +width+1};
		NEIGHBOURS9 = new int[]{-width-1, -width, -width+1, -1, 0, +1, +width-1, +width, +width+1};

				CIRCLE4 = new int[]{-width, +1, +width, -1};
				CIRCLE8 = new int[]{-width-1, -width, -width+1, +1, +width+1, +width, +width-1, -1};

		// allocate A* buffers
		gScore = new int[size];
		fScore = new int[size];
		cameFrom = new int[size];
		closed = new boolean[size];
	}


	// Prevents diagonal corner-cutting: for a diagonal step, both adjacent orthogonal cells must be passable
	private static boolean diagonalClear(int step, int n, boolean[] passable) {
		int off = n - step;
		if (off == -width - 1) {
			return passable[step-1] && passable[step-width];
		} else if (off == -width + 1) {
			return passable[step+1] && passable[step-width];
		} else if (off == +width - 1) {
			return passable[step-1] && passable[step+width];
		} else if (off == +width + 1) {
			return passable[step+1] && passable[step+width];
		}
		return true;
	}


	public static Path find( int from, int to, boolean[] passable ) {

		if (!buildDistanceMap( from, to, passable )) {
			return null;
		}
		
		Path result = new Path();
		int s = from;

		// From the starting position we are moving downwards,
		// until we reach the ending point
		do {
			int minD = distance[s];
			int mins = s;
			
			for (int i=0; i < dir.length; i++) {
				
				int n = s + dir[i];
				
				int thisD = distance[n];
				if (thisD < minD) {
					minD = thisD;
					mins = n;
				}
			}
			s = mins;
			result.add( s );
		} while (s != to);
		
		return result;
	}
	
	public static int getStep( int from, int to, boolean[] passable ) {
		
		if (!buildDistanceMap( from, to, passable )) {
			return -1;
		}
		
		// From the starting position we are making one step downwards
		int minD = distance[from];
		int best = from;
		
		int step, stepD;
		
		for (int i=0; i < dir.length; i++) {

			if ((stepD = distance[step = from + dir[i]]) < minD) {
				minD = stepD;
				best = step;
			}
		}

		return best;
	}
	
	public static int getStepBack( int cur, int from, int lookahead, boolean[] passable, boolean canApproachFromPos ) {

		int d = buildEscapeDistanceMap( cur, from, lookahead, passable );
		if (d == 0) return -1;

		if (!canApproachFromPos) {
			//We can't approach the position we are retreating from
			//re-calculate based on this, and reduce the target distance if need-be
			int head = 0;
			int tail = 0;

			int newD = distance[cur];
			BArray.setFalse(queued);

			queue[tail++] = cur;
			queued[cur] = true;

			while (head < tail) {
				int step = queue[head++];

				if (distance[step] > newD) {
					newD = distance[step];
				}

				int start = (step % width == 0 ? 3 : 0);
				int end = ((step + 1) % width == 0 ? 3 : 0);
				for (int i = start; i < dirLR.length - end; i++) {

					int n = step + dirLR[i];
					if (n >= 0 && n < size && passable[n]) {
						if (distance[n] < distance[cur]) {
							passable[n] = false;
						} else if (distance[n] >= distance[step] && !queued[n]) {
							// Add to queue
							queue[tail++] = n;
							queued[n] = true;
						}
					}
				}

			}

			d = Math.min(newD, d);
		}

		for (int i=0; i < size; i++) {
			goals[i] = distance[i] == d;
		}
		if (!buildDistanceMap( cur, goals, passable )) {
			return -1;
		}

		int s = cur;
		
		// From the starting position we are making one step downwards
		int minD = distance[s];
		int mins = s;
		
		for (int i=0; i < dir.length; i++) {

			int n = s + dir[i];
			int thisD = distance[n];
			
			if (thisD < minD) {
				minD = thisD;
				mins = n;
			}
		}

		return mins;
	}
	
	private static boolean buildDistanceMap( int from, int to, boolean[] passable ) {
		
		if (from == to) {
			return false;
		}

		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		
		boolean pathFound = false;
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = to;
		distance[to] = 0;
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			if (step == from) {
				pathFound = true;
				break;
			}
			int nextDistance = distance[step] + 1;
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n]) {
					// prevent diagonal corner cutting
					if (diagLR[i] && !diagonalClear(step, n, passable)) {
						continue;
					}
					if (n == from || distance[n] > nextDistance) {
						// Add to queue
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
					
			}

		}
		
		return pathFound;
	}
	
	public static void buildDistanceMap( int to, boolean[] passable, int limit ) {
		
		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = to;
		distance[to] = 0;
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			
			int nextDistance = distance[step] + 1;
			if (nextDistance > limit) {
				return;
			}
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n]) {
					if (diagLR[i] && !diagonalClear(step, n, passable)) {
						continue;
					}
					if (distance[n] > nextDistance) {
						// Add to queue
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
					
			}

		}
	}
	
	private static boolean buildDistanceMap( int from, boolean[] to, boolean[] passable ) {
		
		if (to[from]) {
			return false;
		}
		
		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		
		boolean pathFound = false;
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		for (int i=0; i < size; i++) {
			if (to[i]) {
				queue[tail++] = i;
				distance[i] = 0;
			}
		}
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			if (step == from) {
				pathFound = true;
				break;
			}
			int nextDistance = distance[step] + 1;
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n]) {
					// prevent diagonal corner cutting
					if (diagLR[i] && !diagonalClear(step, n, passable)) {
						continue;
					}
					if (n == from || distance[n] > nextDistance) {
						// Add to queue
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
					
			}

		}
		
		return pathFound;
	}

	//the lookahead is the target number of cells to retreat toward from our current position's
	// distance from the position we are escaping from. Returns the highest found distance, up to the lookahead
	private static int buildEscapeDistanceMap( int cur, int from, int lookAhead, boolean[] passable ) {
		
		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		
		int destDist = Integer.MAX_VALUE;
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = from;
		distance[from] = 0;
		
		int dist = 0;
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			dist = distance[step];
			
			if (dist > destDist) {
				return destDist;
			}
			
			if (step == cur) {
				destDist = dist + lookAhead;
			}
			
			int nextDistance = dist + 1;
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n]) {
					if (diagLR[i] && !diagonalClear(step, n, passable)) {
						continue;
					}
					if (distance[n] > nextDistance) {
						// Add to queue
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
					
			}

		}
		
		return dist;
	}
	
	public static void buildDistanceMap( int to, boolean[] passable ) {
		
		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = to;
		distance[to] = 0;
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			int nextDistance = distance[step] + 1;
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n]) {
					if (diagLR[i] && !diagonalClear(step, n, passable)) {
						continue;
					}
					if (distance[n] > nextDistance) {
						// Add to queue
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
					
			}

		}
	}
	
		@SuppressWarnings("serial")
	public static class Path extends LinkedList<Integer> {
	}

	// ==== A* (8-way, octile heuristic, costs: 10 orthogonal, 14 diagonal) ====
	private static int heuristic(int a, int b){
		int ax = a % width;
		int ay = a / width;
		int bx = b % width;
		int by = b / width;
		int dx = Math.abs(ax - bx);
		int dy = Math.abs(ay - by);
		return 10*(dx + dy) - 6*Math.min(dx, dy);
	}

	public static Path findAStar(int from, int to, boolean[] passable){
		if (from == to) return new Path();
		System.arraycopy(maxVal, 0, gScore, 0, maxVal.length);
		System.arraycopy(maxVal, 0, fScore, 0, maxVal.length);
		Arrays.fill(cameFrom, -1);
		Arrays.fill(closed, false);

		gScore[from] = 0;
		fScore[from] = heuristic(from, to);

		PriorityQueue<Integer> open = new PriorityQueue<Integer>(11, new Comparator<Integer>(){
			@Override public int compare(Integer a, Integer b){
				int fa = fScore[a];
				int fb = fScore[b];
				return fa < fb ? -1 : (fa > fb ? 1 : 0);
			}
		});
		open.add(from);

		while (!open.isEmpty()){
			int current = open.poll();
			if (closed[current]) continue;
			closed[current] = true;

			if (current == to){
				Path path = new Path();
				int cur = to;
				while (cur != from && cur != -1){
					path.addFirst(cur);
					cur = cameFrom[cur];
				}
				return path;
			}

			int start = (current % width == 0 ? 3 : 0);
			int end   = ((current+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++){
				int n = current + dirLR[i];
				if (n < 0 || n >= size || !passable[n]) continue;
				if (diagLR[i] && !diagonalClear(current, n, passable)) continue;
				if (closed[n]) continue;

				int stepCost = (i == 1 || i == 3 || i == 4 || i == 6) ? 10 : 14;
				int tentative = gScore[current] + stepCost;
				if (tentative < gScore[n]){
					cameFrom[n] = current;
					gScore[n] = tentative;
					fScore[n] = tentative + heuristic(n, to);
					open.add(n);
				}
			}
		}
		return null;
	}

	public static int getStepAStar(int from, int to, boolean[] passable){
		Path p = findAStar(from, to, passable);
		if (p == null || p.isEmpty()) return -1;
		return p.getFirst();
	}
}

