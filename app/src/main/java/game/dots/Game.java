package game.dots;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class Game extends View {

    //region Declaring variables
    private boolean m_moving;
    private boolean m_vibrate;
    private boolean m_sound;
    private boolean m_gameOver;
    private Rect m_rect;
    private Paint m_paint;
    private Path m_path;
    private Paint m_paintPath;
    private Vibrator m_vibrator;

    private int NUM_CELLS, m_cellWidth, m_cellHeight;
    private int m_score;
    private int m_moves;

    private String m_grid;

    TextView m_scoreView;
    TextView m_movesView;
    ArrayList<Dot> m_dots;
    List<Point> m_dotPath;
    SharedPreferences m_sp;
    MediaPlayer m_mp;

    static List<Animator> animations;
    static AnimatorSet animatorSet;
    //endregion

    //region Constructor, initializing variables
    public Game(Context context, AttributeSet attributeSet) {
        /**Initializing*/
        super(context, attributeSet);
        m_sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        
        m_moving = false;
        m_rect = new Rect();
        m_paint = new Paint();
        m_path = new Path();
        m_paintPath = new Paint();
        m_dots = new ArrayList<>();
        m_dotPath = new ArrayList<>();

        /**Getting values and configuring settings*/
        NUM_CELLS = Integer.parseInt(m_sp.getString("gridSize", "6"));
        m_grid = NUM_CELLS + "x" + NUM_CELLS;

        m_paint.setColor(Color.WHITE);
        m_paint.setStyle(Paint.Style.STROKE);
        m_paint.setStrokeWidth(2);
        m_paint.setAntiAlias(true);

        m_paintPath.setStrokeWidth(10);
        m_paintPath.setStrokeJoin(Paint.Join.ROUND);
        m_paintPath.setStrokeCap(Paint.Cap.ROUND);
        m_paintPath.setStyle(Paint.Style.STROKE);
        m_paintPath.setAntiAlias(true);

        m_vibrate = m_sp.getBoolean("vibrations", false);
        m_vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        m_sound = m_sp.getBoolean("sounds", false);
        m_mp = MediaPlayer.create(getContext(), R.raw.pop);
        m_score = 0;
        m_moves = 30;

        m_gameOver = false;

        animations = new LinkedList<>();
        animatorSet = new AnimatorSet();
    }
    //endregion

    //region Overrides for measuring the screen
    @Override
    protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width  = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int height = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int size = Math.min(width, height);
        setMeasuredDimension(size + getPaddingLeft() + getPaddingRight(),
                size + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        int boardWidth = (xNew - getPaddingLeft() - getPaddingRight());
        int boardHeight = (yNew - getPaddingTop() - getPaddingBottom());
        m_cellWidth = boardWidth / NUM_CELLS;
        m_cellHeight = boardHeight / NUM_CELLS;
    }
    //endregion

    //region Drawing on the canvas
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(m_dots.isEmpty()) {
            createDots();
        }
        /**Draw the grid*/
        for (int row = 0; row < NUM_CELLS; row++) {
            for (int col = 0; col < NUM_CELLS; col++) {
                int x = col * m_cellWidth;
                int y = row * m_cellHeight;
                m_rect.set(x, y, x + m_cellWidth, y + m_cellHeight);
                m_rect.offset(getPaddingLeft(), getPaddingTop());
                canvas.drawRect(m_rect, m_paint);
            }
        }

        /**Draw the connection*/
        if(!m_dotPath.isEmpty() && m_moving) {
            m_path.reset();
            Point point = m_dotPath.get(0);
            m_path.moveTo( (point.x * m_cellWidth) + m_cellWidth / 2, (point.y * m_cellHeight) + m_cellHeight / 2 );
            for( int i = 1; i < m_dotPath.size(); i++ ) {
                point = m_dotPath.get(i);
                m_path.lineTo( (point.x * m_cellWidth) + m_cellWidth / 2, (point.y * m_cellHeight) + m_cellHeight / 2 );
            }
            canvas.drawPath(m_path, m_paintPath);
        }

        /**Draw the dots*/
        for(int i = m_dots.size() - 1; i >= 0; i--) {
            Dot current = m_dots.get(i);
            canvas.drawOval(current.circle, current.dotPaint);
        }
    }
    //endregion

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if(m_gameOver) {
            setScore(0);
            return true;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();
        int squareX = squareN(x);
        int squareY = squareN(y);

        Dot current = getDot(squareX, squareY);

        //region Touch - DOWN
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            m_dotPath.add(new Point(squareX, squareY));
            m_paintPath.setColor(current.color);
            m_moving = true;
            animations.clear();
            animatorSet = new AnimatorSet();
        }
        //endregion

        //region Touch - MOVE
        else if (event.getAction() == MotionEvent.ACTION_MOVE && m_moving) {
            Point currentPoint = new Point(squareX, squareY);

            //region Backwards or Circle
            if(m_dotPath.size() > 1 && m_dotPath.contains(currentPoint)) {
                Point secondLast = m_dotPath.get(m_dotPath.size() - 2);

                //region Going Backwards
                if(secondLast.equals(currentPoint)) {
                    m_dotPath.remove(m_dotPath.size() - 1);
                    invalidate();
                }
                //endregion

                //region Connecting a circle
                else if(!m_dotPath.get(m_dotPath.size()-1).equals(currentPoint)) {
                    m_moving = false;
                    m_dotPath.clear();
                    for (int i = 0; i < m_dots.size(); i++) {
                        Dot currentDot = m_dots.get(i);
                        currentPoint = new Point(currentDot.x, currentDot.y);
                        if (current.color == currentDot.color) {
                            m_dotPath.add(currentPoint);
                        }
                    }
                    setScore(m_dotPath.size());
                    moveDots();
                    feedback();
                    m_dotPath.clear();
                }
                //endregion

            }
            //endregion

            //region Adding dot
            else if(!m_dotPath.contains(currentPoint)) {
                Point lastPoint = m_dotPath.get(m_dotPath.size() - 1);
                Dot lastDot = getDot(lastPoint.x, lastPoint.y);
                if(current.color == lastDot.color) {
                    if(current.adjacent(lastDot)) {
                        m_dotPath.add(currentPoint);
                        invalidate(); //To draw the line
                    }
                }
            }
            //endregion

        }
        //endregion

        //region Touch - UP
        else if (event.getAction() == MotionEvent.ACTION_UP && m_moving) {
            if(m_dotPath.size() > 1) {
                setScore(m_dotPath.size());
                moveDots();
                feedback();
            }
            m_dotPath.clear();
            m_moving = false;
            invalidate();
        }
        //endregion

        return true;
    }

    public void feedback() {
        if(m_vibrate) {
            m_vibrator.vibrate(100);
        }
        if(m_sound) {
            m_mp.seekTo(0);
            m_mp.start();
        }
    }

    public void setScore(int i) {
        View v = (View) getParent();
        m_scoreView = (TextView) v.findViewById(R.id.score);
        m_score += i;
        m_scoreView.setText("Score: " + Integer.toString(m_score));
        m_moves --;
        m_movesView = (TextView) v.findViewById(R.id.moves);
        if(m_moves <= 0 || m_gameOver) {
            Popup p = new Popup(this.getContext(), m_score,m_grid);
            m_score = 0;
            m_moves = 30;
            m_dots.clear();
            createDots();
        }
        m_scoreView.setText("Score: " + Integer.toString(m_score));
        m_movesView.setText("Moves: " + Integer.toString(m_moves));
    }

    private void moveDots() {
        Point currentPoint;
        Dot currentDot = null;
        final Dot lastDot;
        int pathSize = m_dotPath.size();

        //Sorting the path so we always start at the highest
        //y-line where a dot is positioned
        Collections.sort(m_dotPath, new Comparator<Point>() {
            public int compare(Point o1, Point o2) {
                return Integer.compare(o1.y, o2.y);
            }
        });

        Dot temp;
        for(int i = 0; i < pathSize; i++) {
            currentPoint = m_dotPath.get(i);
            int x = currentPoint.x;
            int y = currentPoint.y;
            currentDot = getDot(x, y);
            Dot remember = currentDot;

            while(y-- > 0) {
                temp = getDot(x, y);

                animateMove(temp, currentDot);

                currentDot = temp;
            }

            temp = new Dot(currentDot.x, currentDot.y - 1);
            temp.circle.offsetTo(remember.circle.left, currentDot.circle.top - m_cellHeight);
            animateMove(temp, currentDot);
        }
        lastDot = currentDot;

        animatorSet.playTogether(animations);
        animatorSet.setDuration(200);
        animatorSet.start();
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {}

            @Override
            public void onAnimationEnd(Animator animation) {
                assert lastDot != null;
                lastDot.changeColor();
                m_gameOver = gameOver();
            }

            @Override
            public void onAnimationCancel(Animator animator) {}

            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
    }

    public void animateMove(final Dot from, final Dot to) {

        final float topFrom = from.circle.top;
        final float topTo = to.circle.top;
        //System.out.println("->topFrom: " + topFrom + "\n->topTo: " + topTo + "- - - - - - - -");
        final ValueAnimator animator = new ValueAnimator();
        animator.removeAllUpdateListeners();
        animator.setFloatValues(0.0f, 1.0f);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float ratio = (float) animation.getAnimatedValue();
                int y = (int) ((1.0 - ratio) * topFrom + ratio * topTo);
                from.circle.offsetTo(from.circle.left, y);
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                int x = from.cX;
                int y = from.cY;
                from.circle.set(x, y, m_cellWidth + x, m_cellHeight + y);
                from.circle.offset(getPaddingLeft(), getPaddingTop());
                from.circle.inset(m_cellWidth * 0.2f, m_cellHeight * 0.2f);

                int col = from.color;
                from.changeColor(to.color);
                to.changeColor(col);
                from.circle.offsetTo(from.circle.left, from.circle.top);

            }
        });
        animations.add(animator);
    }

    private boolean gameOver() {
        for(int i = 0; i < m_dots.size(); i++) {
            Dot current = m_dots.get(i);
            int x = current.x;
            int y = current.y;

            if((x+1) < NUM_CELLS) {
                Dot current2 = getDot(x+1, y);
                if(current.color == getDot(x+1, y).color) { return false; }
            }
            if((y+1) < NUM_CELLS) {
                Dot current2 = getDot(x, y+1);
                if(current.color == getDot(x, y+1).color) { return false; }
            }
            if((x-1) >= 0) {
                Dot current2 = getDot(x-1, y);
                if(current.color == getDot(x-1, y).color) { return false; }
            }
            if((y-1) >= 0) {
                Dot current2 = getDot(x, y-1);
                if(current.color == getDot(x, y-1).color) { return false; }
            }
        }
        return true;
    }

    private void createDots() {
        for(int row = 0; row < NUM_CELLS; ++row) {
            for(int col = 0; col < NUM_CELLS; ++col) {
                int x = col * m_cellWidth;
                int y = row * m_cellHeight;
                Dot dot = new Dot(x / m_cellWidth, y / m_cellHeight);
                dot.circle.set(x, y, m_cellWidth + x, m_cellHeight + y);
                dot.circle.offset(getPaddingLeft(), getPaddingTop());
                dot.circle.inset(m_cellWidth * 0.2f, m_cellHeight * 0.2f);
                dot.cX = x;
                dot.cY = y;
                m_dots.add(dot);
            }
        }
        m_gameOver = false;
    }

    private int squareN(int n) {
        int square = n / m_cellHeight; //m_cellHeight == m_cellWidth
        if(square < 0) square = 0;
        if(square >= NUM_CELLS - 1) square = NUM_CELLS - 1;
        return square;
    }

    private Dot getDot(int squareX, int squareY) {
        return m_dots.get((NUM_CELLS * squareY) + squareX);
    }

}