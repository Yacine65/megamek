/*
 * MegaMek - Copyright (C) 2003, 2004 Ben Mazur (bmazur@sev.org)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package megamek.client.ui.swing;

import megamek.client.event.BoardViewEvent;
import megamek.client.ui.Messages;
import megamek.client.ui.swing.widget.MegamekButton;
import megamek.client.ui.swing.widget.SkinSpecification;
import megamek.common.*;
import megamek.common.enums.GamePhase;
import megamek.common.event.GamePhaseChangeEvent;
import megamek.common.event.GameTurnChangeEvent;

import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.*;

public class DeployMinefieldDisplay extends StatusBarPhaseDisplay {
    private static final long serialVersionUID = -1243277953037374936L;

    /**
     * This enumeration lists all of the possible ActionCommands that can be
     * carried out during the deploy minefield phase.  Each command has a string 
     * for the command plus a flag that determines what unit type it is 
     * appropriate for.
     * @author arlith
     */
    public enum Command {
        DEPLOY_MINE_CONV("deployMineConv"),
        DEPLOY_MINE_COM("deployMineCom"),
        DEPLOY_MINE_VIBRA("deployMineVibra"),
        DEPLOY_MINE_ACTIVE("deployMineActive"),
        DEPLOY_MINE_INFERNO("deployMineInferno"),
        REMOVE_MINES("removeMines");
    
        String cmd;
        Command(String c) {
            cmd = c;
        }
        
        public String getCmd() {
            return cmd;
        }
        
        @Override
        public String toString() {
            return cmd;
        }
    }

    // buttons
    protected Map<Command,MegamekButton> buttons;
    
    private boolean deployM = false;
    private boolean deployC = false;
    private boolean deployV = false;
    private boolean deployA = false;
    private boolean deployI = false;
    private boolean remove = false;

    private Player p;
    private Vector<Minefield> deployedMinefields = new Vector<>();

    /**
     * Creates and lays out a new deployment phase display for the specified
     * clientgui.getClient().
     */
    public DeployMinefieldDisplay(ClientGUI clientgui) {
        super(clientgui);
        clientgui.getClient().getGame().addGameListener(this);

        setupStatusBar(Messages
                .getString("DeployMinefieldDisplay.waitingForDeployMinefieldPhase"));

        p = clientgui.getClient().getLocalPlayer();

        buttons = new HashMap<>((int) (Command.values().length * 1.25 + 0.5));
        for (Command cmd : Command.values()) {
            String title = Messages.getString("DeployMinefieldDisplay."
                    + cmd.getCmd());
            MegamekButton newButton = new MegamekButton(title,
                    SkinSpecification.UIComponents.PhaseDisplayButton.getComp());
            String ttKey = "DeployMinefieldDisplay." + cmd.getCmd() + ".tooltip";
            if (Messages.keyExists(ttKey)) {
                newButton.setToolTipText(Messages.getString(ttKey));
            }
            newButton.addActionListener(this);
            newButton.setActionCommand(cmd.getCmd());
            newButton.setEnabled(false);
            buttons.put(cmd, newButton);
        }          
        numButtonGroups = (int) Math.ceil((buttons.size()+0.0) / buttonsPerGroup);
        
        butDone.setText(Messages.getString("DeployMinefieldDisplay.Done"));
        butDone.setEnabled(false);
        
        setupButtonPanel();
    }

    @Override
    protected ArrayList<MegamekButton> getButtonList() {
        ArrayList<MegamekButton> buttonList = new ArrayList<>();
        for (Command cmd : Command.values()) {
            buttonList.add(buttons.get(cmd));
        }
        return buttonList;
    }

    /**
     * Enables relevant buttons and sets up for your turn.
     */
    private void beginMyTurn() {
        p = clientgui.getClient().getLocalPlayer();// necessary to make it work after resets.
        setConventionalEnabled(p.getNbrMFConventional());
        setCommandEnabled(p.getNbrMFCommand());
        setVibrabombEnabled(p.getNbrMFVibra());
        setActiveEnabled(p.getNbrMFActive());
        setInfernoEnabled(p.getNbrMFInferno());
        setRemoveMineEnabled(true);
        butDone.setEnabled(true);
    }

    /**
     * Clears out old deployment data and disables relevant buttons.
     */
    private void endMyTurn() {
        // end my turn, then.
        disableButtons();
        clientgui.getBoardView().select(null);
        clientgui.getBoardView().highlight(null);
        clientgui.getBoardView().cursor(null);

    }

    /**
     * Disables all buttons in the interface
     */
    private void disableButtons() {
        setConventionalEnabled(0);
        setCommandEnabled(0);
        setVibrabombEnabled(0);
        setActiveEnabled(0);
        setInfernoEnabled(0);
        setRemoveMineEnabled(false);

        butDone.setEnabled(false);
    }

    private void deployMinefield(Coords coords) {
        if (!clientgui.getClient().getGame().getBoard().contains(coords)) {
            return;
        }

        // check if this is a water hex
        boolean sea = false;
        Hex hex = clientgui.getClient().getGame().getBoard().getHex(coords);
        if (hex.containsTerrain(Terrains.WATER)) {
            sea = true;
        }

        if (remove) {
            if (!clientgui.getClient().getGame().containsMinefield(coords)) {
                return;
            }
            Enumeration<?> mfs = clientgui.getClient().getGame().getMinefields(coords).elements();
            ArrayList<Minefield> mfRemoved = new ArrayList<>();
            while (mfs.hasMoreElements()) {
                Minefield mf = (Minefield) mfs.nextElement();
                if (mf.getPlayerId() == clientgui.getClient().getLocalPlayer().getId()) {
                    mfRemoved.add(mf);
                    deployedMinefields.removeElement(mf);
                    if (mf.getType() == Minefield.TYPE_CONVENTIONAL) {
                        p.setNbrMFConventional(p.getNbrMFConventional() + 1);
                    } else if (mf.getType() == Minefield.TYPE_COMMAND_DETONATED) {
                        p.setNbrMFCommand(p.getNbrMFCommand() + 1);
                    } else if (mf.getType() == Minefield.TYPE_VIBRABOMB) {
                        p.setNbrMFVibra(p.getNbrMFVibra() + 1);
                    } else if (mf.getType() == Minefield.TYPE_ACTIVE) {
                        p.setNbrMFActive(p.getNbrMFActive() + 1);
                    } else if (mf.getType() == Minefield.TYPE_INFERNO) {
                        p.setNbrMFInferno(p.getNbrMFInferno() + 1);
                    }
                }
            }

            for (Minefield mf : mfRemoved) {
                clientgui.getClient().getGame().removeMinefield(mf);
            }
        } else {
            // first check that there is not already a mine of this type
            // deployed
            Enumeration<?> mfs = clientgui.getClient().getGame().getMinefields(coords).elements();
            while (mfs.hasMoreElements()) {
                Minefield mf = (Minefield) mfs.nextElement();
                if ((deployM && (mf.getType() == Minefield.TYPE_CONVENTIONAL))
                        || (deployC && (mf.getType() == Minefield.TYPE_COMMAND_DETONATED))
                        || (deployV && (mf.getType() == Minefield.TYPE_VIBRABOMB))
                        || (deployA && (mf.getType() == Minefield.TYPE_ACTIVE))
                        || (deployI && (mf.getType() == Minefield.TYPE_INFERNO))) {
                    clientgui.doAlertDialog(Messages.getString("DeployMinefieldDisplay.IllegalPlacement"),
                            Messages.getString("DeployMinefieldDisplay.DuplicateMinefield"));
                    return;
                }
            }

            Minefield mf = null;
            if (sea && !(deployM || deployI)) {
                clientgui.doAlertDialog(Messages.getString("DeployMinefieldDisplay.IllegalPlacement"),
                        Messages.getString("DeployMinefieldDisplay.WaterPlacement"));
                return;
            }
            int depth = 0;
            if (deployM) {
                if (sea) {
                    SeaMineDepthDialog smd = new SeaMineDepthDialog(
                            clientgui.frame, hex.depth());
                    smd.setVisible(true);

                    depth = smd.getDepth();
                }
                MineDensityDialog mfd = new MineDensityDialog(clientgui.frame);
                mfd.setVisible(true);
                
                if (mfd.getDensity() > 0) {
                    mf = Minefield.createMinefield(coords, p.getId(),
                            Minefield.TYPE_CONVENTIONAL, mfd.getDensity(), sea,
                            depth);
                    p.setNbrMFConventional(p.getNbrMFConventional() - 1);
                }
            } else if (deployC) {
                MineDensityDialog mfd = new MineDensityDialog(clientgui.frame);
                mfd.setVisible(true);

                if (mfd.getDensity() > 0) {
                    mf = Minefield.createMinefield(coords, p.getId(),
                            Minefield.TYPE_COMMAND_DETONATED, mfd.getDensity(),
                            sea, depth);
                    p.setNbrMFCommand(p.getNbrMFCommand() - 1);
                }
            } else if (deployA) {
                MineDensityDialog mfd = new MineDensityDialog(clientgui.frame);
                mfd.setVisible(true);

                if (mfd.getDensity() > 0) {
                    mf = Minefield.createMinefield(coords, p.getId(),
                            Minefield.TYPE_ACTIVE, mfd.getDensity());
                    p.setNbrMFActive(p.getNbrMFActive() - 1);
                }
            } else if (deployI) {
                MineDensityDialog mfd = new MineDensityDialog(clientgui.frame);
                mfd.setVisible(true);

                if (mfd.getDensity() > 0) {
                    mf = Minefield.createMinefield(coords, p.getId(),
                            Minefield.TYPE_INFERNO, mfd.getDensity(), sea,
                            depth);
                    p.setNbrMFInferno(p.getNbrMFInferno() - 1);
                }
            } else if (deployV) {
                MineDensityDialog mfd = new MineDensityDialog(clientgui.frame);
                mfd.setVisible(true);

                VibrabombSettingDialog vsd = new VibrabombSettingDialog(
                        clientgui.frame);
                vsd.setVisible(true);

                if (mfd.getDensity() > 0) {
                    mf = Minefield.createMinefield(coords, p.getId(),
                            Minefield.TYPE_VIBRABOMB, mfd.getDensity(),
                            vsd.getSetting());
                    p.setNbrMFVibra(p.getNbrMFVibra() - 1);
                }
            } else {
                return;
            }
            if (mf != null) {
                mf.setWeaponDelivered(false);
                clientgui.getClient().getGame().addMinefield(mf);
                deployedMinefields.addElement(mf);
            }
            clientgui.getBoardView().refreshDisplayables();
        }

        setConventionalEnabled(p.getNbrMFConventional());
        setCommandEnabled(p.getNbrMFCommand());
        setVibrabombEnabled(p.getNbrMFVibra());
        setActiveEnabled(p.getNbrMFActive());
        setInfernoEnabled(p.getNbrMFInferno());

        if (p.getNbrMFConventional() == 0) {
            deployM = false;
        }
        if (p.getNbrMFCommand() == 0) {
            deployC = false;
        }
        if (p.getNbrMFVibra() == 0) {
            deployV = false;
        }
        if (p.getNbrMFActive() == 0) {
            deployA = false;
        }
        if (p.getNbrMFInferno() == 0) {
            deployI = false;
        }

    }

    @Override
    public void clear() {
        //TODO: undefined for now
    }

    //
    // BoardListener
    //
    @Override
    public void hexMoused(BoardViewEvent b) {

        // Are we ignoring events?
        if (isIgnoringEvents()) {
            return;
        }

        if (b.getType() != BoardViewEvent.BOARD_HEX_DRAGGED) {
            return;
        }

        // ignore buttons other than 1
        if (!clientgui.getClient().isMyTurn()
                || ((b.getButton() != MouseEvent.BUTTON1))) {
            return;
        }

        // check for a deployment
        clientgui.getBoardView().select(b.getCoords());
        deployMinefield(b.getCoords());
    }

    //
    // GameListener
    //
    @Override
    public void gameTurnChange(GameTurnChangeEvent e) {
        // Are we ignoring events?
        if (isIgnoringEvents()) {
            return;
        }

        endMyTurn();

        if (clientgui.getClient().isMyTurn()) {
            beginMyTurn();
            setStatusBarText(Messages
                    .getString("DeployMinefieldDisplay.its_your_turn"));
        } else {
            String playerName;
            if (e.getPlayer() != null) {
                playerName = e.getPlayer().getName();
            } else {
                playerName = "Unknown";
            }
            setStatusBarText(Messages.getString("DeployMinefieldDisplay."
                    + "its_others_turn", playerName));
        }
    }

    @Override
    public void gamePhaseChange(GamePhaseChangeEvent e) {
        // Are we ignoring events?
        if (isIgnoringEvents()) {
            return;
        }

        if (clientgui.getClient().isMyTurn()
                && (clientgui.getClient().getGame().getPhase() != GamePhase.DEPLOY_MINEFIELDS)) {
            endMyTurn();
        }
        if (clientgui.getClient().getGame().getPhase() == GamePhase.DEPLOY_MINEFIELDS) {
            setStatusBarText(Messages
                    .getString("DeployMinefieldDisplay.waitingForDeploymentPhase"));
        }
    }

    //
    // ActionListener
    //
    @Override
    public void actionPerformed(ActionEvent ev) {
        // Are we ignoring events?
        if (isIgnoringEvents()) {
            return;
        }

        if (!clientgui.getClient().isMyTurn()) {
            // odd...
            return;
        } else if (ev.getActionCommand().equals(Command.DEPLOY_MINE_CONV.getCmd())) {
            deployM = true;
            deployC = false;
            deployV = false;
            deployA = false;
            deployI = false;
            remove = false;
        } else if (ev.getActionCommand().equals(Command.DEPLOY_MINE_COM.getCmd())) {
            deployM = false;
            deployC = true;
            deployV = false;
            deployA = false;
            deployI = false;
            remove = false;
        } else if (ev.getActionCommand().equals(Command.DEPLOY_MINE_VIBRA.getCmd())) {
            deployM = false;
            deployC = false;
            deployV = true;
            deployA = false;
            deployI = false;
            remove = false;
        } else if (ev.getActionCommand().equals(Command.DEPLOY_MINE_ACTIVE.getCmd())) {
            deployM = false;
            deployC = false;
            deployV = false;
            deployA = true;
            deployI = false;
            remove = false;
        } else if (ev.getActionCommand().equals(Command.DEPLOY_MINE_INFERNO.getCmd())) {
            deployM = false;
            deployC = false;
            deployV = false;
            deployA = false;
            deployI = true;
            remove = false;
        } else if (ev.getActionCommand().equals(Command.REMOVE_MINES.getCmd())) {
            deployM = false;
            deployC = false;
            deployV = false;
            deployA = false;
            deployI = false;
            remove = true;
        }
    } // End public void actionPerformed(ActionEvent ev)

    @Override
    public void ready() {
        endMyTurn();
        clientgui.getClient().sendDeployMinefields(deployedMinefields);
        clientgui.getClient().sendPlayerInfo();
    }

    private void setConventionalEnabled(int nbr) {
        buttons.get(Command.DEPLOY_MINE_CONV).setText(Messages.getString(
                "DeployMinefieldDisplay." + Command.DEPLOY_MINE_CONV.getCmd(), nbr));
        buttons.get(Command.DEPLOY_MINE_CONV).setEnabled(nbr > 0);
    }

    private void setCommandEnabled(int nbr) {
        buttons.get(Command.DEPLOY_MINE_COM).setText(Messages.getString(
                "DeployMinefieldDisplay." + Command.DEPLOY_MINE_COM.getCmd(), nbr));
        buttons.get(Command.DEPLOY_MINE_COM).setEnabled(nbr > 0);
    }

    private void setVibrabombEnabled(int nbr) {
        buttons.get(Command.DEPLOY_MINE_VIBRA).setText(Messages.getString(
                "DeployMinefieldDisplay." + Command.DEPLOY_MINE_VIBRA.getCmd(), nbr));
        buttons.get(Command.DEPLOY_MINE_VIBRA).setEnabled(nbr > 0);
    }

    private void setActiveEnabled(int nbr) {
        buttons.get(Command.DEPLOY_MINE_ACTIVE).setText(Messages.getString(
                "DeployMinefieldDisplay." + Command.DEPLOY_MINE_ACTIVE.getCmd(), nbr));
        buttons.get(Command.DEPLOY_MINE_ACTIVE).setEnabled(nbr > 0);
    }

    private void setInfernoEnabled(int nbr) {
        buttons.get(Command.DEPLOY_MINE_INFERNO).setText(Messages.getString(
                "DeployMinefieldDisplay." + Command.DEPLOY_MINE_INFERNO.getCmd(), nbr));
        buttons.get(Command.DEPLOY_MINE_INFERNO).setEnabled(nbr > 0);
    }

    private void setRemoveMineEnabled(boolean enable) {
        buttons.get(Command.REMOVE_MINES).setEnabled(enable);
    }

    /**
     * Stop just ignoring events and actually stop listening to them.
     */
    @Override
    public void removeAllListeners() {
        clientgui.getClient().getGame().removeGameListener(this);
        clientgui.getBoardView().removeBoardViewListener(this);
    }
}
