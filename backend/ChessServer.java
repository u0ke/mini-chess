import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChessServer {
    static final int EMPTY = 0, WP=1, WN=2, WB=3, WR=4, WQ=5, WK=6;
    static final int BP=-1, BN=-2, BB=-3, BR=-4, BQ=-5, BK=-6;

    static final Object LOCK = new Object();
    static Game game = new Game(400);

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/state", ChessServer::state);
        server.createContext("/legal", ChessServer::legal);
        server.createContext("/move", ChessServer::move);
        server.createContext("/new", ChessServer::newGame);
        server.createContext("/", ChessServer::root);
        server.setExecutor(null);
        server.start();
        System.out.println("Mini Chess backend running on http://localhost:8080");
    }

    static void root(HttpExchange ex) throws IOException {
        send(ex, 200, "Mini Chess Java backend is running.");
    }

    static void state(HttpExchange ex) throws IOException {
        synchronized (LOCK) {
            send(ex, 200, game.toJson());
        }
    }

    static void legal(HttpExchange ex) throws IOException {
        Map<String,String> q = query(ex);
        int r = Integer.parseInt(q.getOrDefault("row","-1"));
        int c = Integer.parseInt(q.getOrDefault("col","-1"));
        synchronized (LOCK) {
            List<Move> moves = game.legalMovesFrom(r,c);
            send(ex, 200, "[" + joinMoves(moves) + "]");
        }
    }

    static void move(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        int fr = number(body, "fromRow"), fc = number(body, "fromCol");
        int tr = number(body, "toRow"), tc = number(body, "toCol");

        synchronized (LOCK) {
            game.playerMove(fr,fc,tr,tc);
            send(ex, 200, game.toJson());
        }
    }

    static void newGame(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        int elo = number(body, "elo");
        if (elo < 100) elo = 100;
        if (elo > 900) elo = 900;
        synchronized (LOCK) {
            game = new Game(elo);
            send(ex, 200, game.toJson());
        }
    }

    static String joinMoves(List<Move> ms) {
        StringBuilder s = new StringBuilder();
        for (int i=0;i<ms.size();i++) {
            if (i>0) s.append(",");
            s.append(ms.get(i).toJson());
        }
        return s.toString();
    }

    static Map<String,String> query(HttpExchange ex) {
        Map<String,String> m = new HashMap<>();
        String q = ex.getRequestURI().getQuery();
        if (q != null) for (String p:q.split("&")) {
            String[] a=p.split("=",2);
            if(a.length==2)m.put(a[0],a[1]);
        }
        return m;
    }

    static int number(String s,String key) {
        String p="\"" + key + "\"";
        int i=s.indexOf(p);
        if(i<0) return 0;
        i=s.indexOf(':',i)+1;
        while(i<s.length() && !Character.isDigit(s.charAt(i)) && s.charAt(i)!='-') i++;
        int j=i+1;
        while(j<s.length() && Character.isDigit(s.charAt(j))) j++;
        return Integer.parseInt(s.substring(i,j));
    }

    static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static void send(HttpExchange ex,int code,String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
        byte[] b=body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code,b.length);
        try(OutputStream os=ex.getResponseBody()){os.write(b);}
    }

    static class Move {
        int fr,fc,tr,tc,piece,captured;
        boolean castle, enPassant;
        Move(int fr,int fc,int tr,int tc,int piece,int captured){
            this.fr=fr;this.fc=fc;this.tr=tr;this.tc=tc;this.piece=piece;this.captured=captured;
        }
        String toJson(){
            return "{\"toRow\":"+tr+",\"toCol\":"+tc+"}";
        }
    }

    static class Game {
        int[][] b = new int[8][8];
        boolean whiteTurn=true, gameOver=false;
        boolean wkc=true,wqc=true,bkc=true,bqc=true;
        int epR=-1,epC=-1;
        int elo;
        List<String> moves=new ArrayList<>();
        Random random=new Random();

        Game(int elo){this.elo=elo;setup();}

        void setup(){
            int[] back={WR,WN,WB,WQ,WK,WB,WN,WR};
            int[] black={BR,BN,BB,BQ,BK,BB,BN,BR};
            for(int c=0;c<8;c++){
                b[0][c]=black[c]; b[1][c]=BP;
                b[6][c]=WP; b[7][c]=back[c];
            }
        }

        boolean white(int p){return p>0;}
        boolean black(int p){return p<0;}

        List<Move> legalMovesFrom(int r,int c){
            List<Move> out=new ArrayList<>();
            if(r<0||r>7||c<0||c>7||b[r][c]==0)return out;
            if((whiteTurn && !white(b[r][c]))||(!whiteTurn && !black(b[r][c])))return out;
            for(Move m: pseudoMoves(r,c)){
                if(!leavesKingSafe(m,whiteTurn))continue;
                out.add(m);
            }
            return out;
        }

        List<Move> legalAll(boolean sideWhite){
            List<Move> out=new ArrayList<>();
            for(int r=0;r<8;r++)for(int c=0;c<8;c++){
                int p=b[r][c];
                if(p==0 || white(p)!=sideWhite)continue;
                for(Move m:pseudoMoves(r,c))
                    if(leavesKingSafe(m,sideWhite))out.add(m);
            }
            return out;
        }

        List<Move> pseudoMoves(int r,int c){
            List<Move> ms=new ArrayList<>();
            int p=b[r][c], ap=Math.abs(p);
            if(ap==WP){
                int d=white(p)?-1:1, start=white(p)?6:1, nr=r+d;
                if(in(nr,c)&&b[nr][c]==0){
                    ms.add(new Move(r,c,nr,c,p,0));
                    if(r==start && b[r+2*d][c]==0)
                        ms.add(new Move(r,c,r+2*d,c,p,0));
                }
                for(int dc:new int[]{-1,1}){
                    int nc=c+dc;
                    if(!in(nr,nc))continue;
                    if(b[nr][nc]!=0 && white(b[nr][nc])!=white(p))
                        ms.add(new Move(r,c,nr,nc,p,b[nr][nc]));
                    if(nr==epR && nc==epC){
                        Move m=new Move(r,c,nr,nc,p,white(p)?BP:WP);
                        m.enPassant=true; ms.add(m);
                    }
                }
            } else if(ap==WN){
                int[][] ds={{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
                for(int[]d:ds)addIf(ms,r,c,r+d[0],c+d[1]);
            } else if(ap==WK){
                for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++)
                    if(dr!=0||dc!=0)addIf(ms,r,c,r+dr,c+dc);
                if(white(p)&&r==7&&c==4&&!inCheck(true)){
                    if(wkc&&b[7][5]==0&&b[7][6]==0&&!attacked(7,5,false)&&!attacked(7,6,false)){
                        Move m=new Move(7,4,7,6,p,0);m.castle=true;ms.add(m);
                    }
                    if(wqc&&b[7][1]==0&&b[7][2]==0&&b[7][3]==0&&!attacked(7,3,false)&&!attacked(7,2,false)){
                        Move m=new Move(7,4,7,2,p,0);m.castle=true;ms.add(m);
                    }
                }
                if(black(p)&&r==0&&c==4&&!inCheck(false)){
                    if(bkc&&b[0][5]==0&&b[0][6]==0&&!attacked(0,5,true)&&!attacked(0,6,true)){
                        Move m=new Move(0,4,0,6,p,0);m.castle=true;ms.add(m);
                    }
                    if(bqc&&b[0][1]==0&&b[0][2]==0&&b[0][3]==0&&!attacked(0,3,true)&&!attacked(0,2,true)){
                        Move m=new Move(0,4,0,2,p,0);m.castle=true;ms.add(m);
                    }
                }
            } else {
                int[][] dirs;
                if(ap==WB) dirs=new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}};
                else if(ap==WR) dirs=new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
                else dirs=new int[][]{{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}};
                for(int[]d:dirs){
                    int nr=r+d[0],nc=c+d[1];
                    while(in(nr,nc)){
                        if(b[nr][nc]==0)ms.add(new Move(r,c,nr,nc,p,0));
                        else {if(white(b[nr][nc])!=white(p))ms.add(new Move(r,c,nr,nc,p,b[nr][nc]));break;}
                        nr+=d[0];nc+=d[1];
                    }
                }
            }
            return ms;
        }

        void addIf(List<Move>ms,int fr,int fc,int tr,int tc){
            if(!in(tr,tc))return;
            if(b[tr][tc]==0||white(b[tr][tc])!=white(b[fr][fc]))
                ms.add(new Move(fr,fc,tr,tc,b[fr][fc],b[tr][tc]));
        }

        boolean in(int r,int c){return r>=0&&r<8&&c>=0&&c<8;}

        boolean leavesKingSafe(Move m,boolean side){
            Game g=copy();
            g.apply(m,false);
            return !g.inCheck(side);
        }

        boolean inCheck(boolean sideWhite){
            int king=sideWhite?WK:BK;
            for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(b[r][c]==king)
                return attacked(r,c,!sideWhite);
            return true;
        }

        boolean attacked(int r,int c,boolean byWhite){
            // Pawns
            int pr=r+(byWhite?1:-1);
            for(int dc:new int[]{-1,1}) if(in(pr,c+dc)&&b[pr][c+dc]==(byWhite?WP:BP))return true;
            // Knights
            int[][] nd={{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
            for(int[]d:nd)if(in(r+d[0],c+d[1])&&b[r+d[0]][c+d[1]]==(byWhite?WN:BN))return true;
            // King
            for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++)
                if((dr!=0||dc!=0)&&in(r+dr,c+dc)&&b[r+dr][c+dc]==(byWhite?WK:BK))return true;
            int[][] diag={{1,1},{1,-1},{-1,1},{-1,-1}};
            for(int[]d:diag){
                int nr=r+d[0],nc=c+d[1];
                while(in(nr,nc)){
                    int p=b[nr][nc];
                    if(p!=0){if(p==(byWhite?WB:BB)||p==(byWhite?WQ:BQ))return true;break;}
                    nr+=d[0];nc+=d[1];
                }
            }
            int[][] straight={{1,0},{-1,0},{0,1},{0,-1}};
            for(int[]d:straight){
                int nr=r+d[0],nc=c+d[1];
                while(in(nr,nc)){
                    int p=b[nr][nc];
                    if(p!=0){if(p==(byWhite?WR:BR)||p==(byWhite?WQ:BQ))return true;break;}
                    nr+=d[0];nc+=d[1];
                }
            }
            return false;
        }

        void playerMove(int fr,int fc,int tr,int tc){
            if(gameOver||!whiteTurn)throw new IllegalArgumentException("Not your turn");
            Move chosen=null;
            for(Move m:legalMovesFrom(fr,fc))if(m.tr==tr&&m.tc==tc){chosen=m;break;}
            if(chosen==null)throw new IllegalArgumentException("Illegal move");
            String notation=notation(chosen);
            apply(chosen,true);
            moves.add(notation);
            afterMove();
            if(!gameOver){
                Move bot=Bot.choose(this,elo);
                String bn=notation(bot);
                apply(bot,true);
                moves.add(bn);
                afterMove();
            }
        }

        void afterMove(){
            boolean side=whiteTurn;
            List<Move> lm=legalAll(side);
            if(lm.isEmpty()){
                gameOver=true;
            }
        }

        String result(){
            if(!gameOver)return "";
            boolean side=whiteTurn;
            if(inCheck(side))return side?"Checkmate — Black wins":"Checkmate — White wins";
            return "Draw — stalemate";
        }

        String notation(Move m){
            String s;
            if(m.castle)s=(m.tc==6?"O-O":"O-O-O");
            else{
                String[] names={"","","N","B","R","Q","K"};
                String name=names[Math.abs(m.piece)];
                s=name+(m.captured!=0||m.enPassant?file(m.fc)+"x":"")+file(m.tc)+(8-m.tr);
                if(Math.abs(m.piece)==1&&m.tr==0||Math.abs(m.piece)==1&&m.tr==7)s+="=Q";
            }
            return s;
        }
        char file(int c){return (char)('a'+c);}

        void apply(Move m,boolean real){
            int p=b[m.fr][m.fc];
            b[m.fr][m.fc]=0;
            if(m.enPassant)b[m.tr+(white(p)?1:-1)][m.tc]=0;
            b[m.tr][m.tc]=p;

            if(Math.abs(p)==WP && (m.tr==0||m.tr==7))b[m.tr][m.tc]=white(p)?WQ:BQ;

            if(m.castle){
                if(m.tc==6){b[m.tr][5]=b[m.tr][7];b[m.tr][7]=0;}
                else {b[m.tr][3]=b[m.tr][0];b[m.tr][0]=0;}
            }

            if(p==WK)wkc=wqc=false;
            if(p==BK)bkc=bqc=false;
            if(m.fr==7&&m.fc==0)wqc=false;
            if(m.fr==7&&m.fc==7)wkc=false;
            if(m.fr==0&&m.fc==0)bqc=false;
            if(m.fr==0&&m.fc==7)bkc=false;

            if(m.tr==7&&m.tc==0&&m.captured==WR)wqc=false;
            if(m.tr==7&&m.tc==7&&m.captured==WR)wkc=false;
            if(m.tr==0&&m.tc==0&&m.captured==BR)bqc=false;
            if(m.tr==0&&m.tc==7&&m.captured==BR)bkc=false;

            epR=-1;epC=-1;
            if(Math.abs(p)==WP && Math.abs(m.tr-m.fr)==2){
                epR=(m.fr+m.tr)/2;epC=m.fc;
            }

            whiteTurn=!whiteTurn;
        }

        Game copy(){
            Game g=new Game(elo);
            for(int r=0;r<8;r++)g.b[r]=b[r].clone();
            g.whiteTurn=whiteTurn;g.gameOver=gameOver;
            g.wkc=wkc;g.wqc=wqc;g.bkc=bkc;g.bqc=bqc;
            g.epR=epR;g.epC=epC;
            g.moves=new ArrayList<>(moves);
            return g;
        }

        String toJson(){
            StringBuilder s=new StringBuilder("{\"board\":[");
            for(int r=0;r<8;r++){
                if(r>0)s.append(",");
                s.append("[");
                for(int c=0;c<8;c++){
                    if(c>0)s.append(",");
                    s.append("\"").append(pieceCode(b[r][c])).append("\"");
                }
                s.append("]");
            }
            s.append("],\"turn\":\"").append(whiteTurn?"w":"b").append("\"");
            s.append(",\"elo\":").append(elo);
            s.append(",\"gameOver\":").append(gameOver);
            s.append(",\"result\":\"").append(json(result())).append("\"");
            s.append(",\"moves\":[");
            for(int i=0;i<moves.size();i++){if(i>0)s.append(",");s.append("\"").append(json(moves.get(i))).append("\"");}
            s.append("],\"legalMoves\":[]}");
            return s.toString();
        }

        String pieceCode(int p){
            if(p==0)return "";
            char color=p>0?'w':'b';
            char type=switch(Math.abs(p)){case 1->'P';case 2->'N';case 3->'B';case 4->'R';case 5->'Q';default->'K';};
            return ""+color+type;
        }
        String json(String x){return x.replace("\\","\\\\").replace("\"","\\\"");}
    }

    static class Bot {
        static Move choose(Game g,int elo){
            List<Move> ms=g.legalAll(false);
            if(ms.isEmpty())return null;
            int depth = elo<=250?1:elo<=500?2:elo<=750?2:3;

            // Very low levels deliberately make imperfect/random choices.
            if(elo<=250)return ms.get(g.random.nextInt(ms.size()));
            if(elo<=400 && g.random.nextDouble()<0.35)return ms.get(g.random.nextInt(ms.size()));
            if(elo<=500 && g.random.nextDouble()<0.18)return ms.get(g.random.nextInt(ms.size()));

            int best=Integer.MIN_VALUE;
            List<Move> bestMoves=new ArrayList<>();
            for(Move m:ms){
                Game n=g.copy(); n.apply(m,false);
                int score=minimax(n,depth-1,Integer.MIN_VALUE+1,Integer.MAX_VALUE-1,true);
                // Small randomness keeps equal moves from always being identical.
                score += g.random.nextInt(5);
                if(score>best){best=score;bestMoves.clear();bestMoves.add(m);}
                else if(score==best)bestMoves.add(m);
            }
            return bestMoves.get(g.random.nextInt(bestMoves.size()));
        }

        static int minimax(Game g,int depth,int alpha,int beta,boolean maximizingWhite){
            List<Move> ms=g.legalAll(maximizingWhite);
            if(depth==0||ms.isEmpty()){
                if(ms.isEmpty()){
                    if(g.inCheck(maximizingWhite))return maximizingWhite?-100000:100000;
                    return 0;
                }
                return evaluate(g);
            }
            if(maximizingWhite){
                int v=Integer.MIN_VALUE;
                for(Move m:ms){
                    Game n=g.copy();n.apply(m,false);
                    v=Math.max(v,minimax(n,depth-1,alpha,beta,false));
                    alpha=Math.max(alpha,v);if(beta<=alpha)break;
                }
                return v;
            }else{
                int v=Integer.MAX_VALUE;
                for(Move m:ms){
                    Game n=g.copy();n.apply(m,false);
                    v=Math.min(v,minimax(n,depth-1,alpha,beta,true));
                    beta=Math.min(beta,v);if(beta<=alpha)break;
                }
                return v;
            }
        }

        static int evaluate(Game g){
            int score=0;
            int[] val={0,100,320,330,500,900,20000};
            for(int r=0;r<8;r++)for(int c=0;c<8;c++){
                int p=g.b[r][c];
                if(p!=0){
                    int v=val[Math.abs(p)];
                    // Small center bonus.
                    int center=(3-Math.abs(3-r))+(3-Math.abs(3-c));
                    v+=center*4;
                    score += p>0?v:-v;
                }
            }
            return score;
        }
    }
}
