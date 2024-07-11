package org.jkiss.dbeaver.ui.dialogs.connection;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.core.CoreMessages;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceFolder;
import org.jkiss.dbeaver.model.DBPDataSourcePermission;
import org.jkiss.dbeaver.model.DBPDataSourceProvider;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPConnectionType;
import org.jkiss.dbeaver.model.navigator.DBNBrowseSettings;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.DBSObjectFilter;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.dbeaver.model.struct.rdb.DBSTable;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.registry.DataSourceNavigatorSettings;
import org.jkiss.dbeaver.registry.DataSourceProviderRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.IHelpContextIds;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.contentassist.ContentAssistUtils;
import org.jkiss.dbeaver.ui.contentassist.SmartTextContentAdapter;
import org.jkiss.dbeaver.ui.contentassist.StringContentProposalProvider;
import org.jkiss.dbeaver.ui.controls.CSmartCombo;
import org.jkiss.dbeaver.ui.controls.ConnectionFolderSelector;
import org.jkiss.dbeaver.ui.navigator.dialogs.EditObjectFilterDialog;
import org.jkiss.dbeaver.ui.preferences.PrefPageConnectionTypes;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.dbeaver.ui.dialogs.connection.ConnectionPageGeneralHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConnectionPageGeneralHelper {


    public static void updateNavigatorSettingsPreset(Combo navigatorSettingsCombo, DBNBrowseSettings navigatorSettings) {
        // Find first preset that matches current connection settings
        boolean isPreset = false;
        for (DataSourceNavigatorSettings.Preset nsEntry : DataSourceNavigatorSettings.PRESETS.values()) {
            if (navigatorSettings.equals(nsEntry.getSettings())) {
                navigatorSettingsCombo.setText(nsEntry.getName());
                isPreset = true;
                break;
            }
        }
        if (!isPreset) {
            navigatorSettingsCombo.select(navigatorSettingsCombo.getItemCount() - 1);
        }
    }

    public static Combo createNavigatorSettingsCombo(Composite composite, NavigatorSettingsStorage settingsStorage, DBPDataSourceContainer dataSourceDescriptor) {
        UIUtils.createControlLabel(composite, CoreMessages.dialog_connection_wizard_final_label_navigator_settings);

        Composite ctGroup = UIUtils.createComposite(composite, 2);
        Combo navigatorSettingsCombo = new Combo(ctGroup, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
        final GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
        gd.widthHint = UIUtils.getFontHeight(navigatorSettingsCombo) * 20;
        navigatorSettingsCombo.setLayoutData(gd);
        for (String ncPresetName : DataSourceNavigatorSettings.PRESETS.keySet()) {
            navigatorSettingsCombo.add(ncPresetName);
        }
        navigatorSettingsCombo.select(0);
        navigatorSettingsCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (navigatorSettingsCombo.getSelectionIndex() == navigatorSettingsCombo.getItemCount() - 1) {
                    // Custom - no changes
                } else {
                    DataSourceNavigatorSettings.Preset newSettings = DataSourceNavigatorSettings.PRESETS.get(navigatorSettingsCombo.getText());
                    if (newSettings == null) {
                        throw new IllegalStateException("Invalid preset name: " + navigatorSettingsCombo.getText());
                    }
                    settingsStorage.setNavigatorSettings(newSettings.getSettings());
                }
            }
        });

        UIUtils.createDialogButton(ctGroup, CoreMessages.dialog_connection_wizard_final_label_navigator_settings_customize, new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                settingsStorage.setNavigatorSettings(
                        editNavigatorSettings(navigatorSettingsCombo, settingsStorage.getNavigatorSettings(), dataSourceDescriptor));
            }
        });
        return navigatorSettingsCombo;
    }

    private static DBNBrowseSettings editNavigatorSettings(
            @NotNull Combo navigatorSettingsCombo,
            @NotNull DBNBrowseSettings navigatorSettings,
            @Nullable DBPDataSourceContainer dataSourceDescriptor) {
        EditConnectionNavigatorSettingsDialog dialog = new EditConnectionNavigatorSettingsDialog(
                navigatorSettingsCombo.getShell(),
                navigatorSettings,
                dataSourceDescriptor);
        if (dialog.open() == IDialogConstants.OK_ID) {
            navigatorSettings = dialog.getNavigatorSettings();
            ConnectionPageGeneralHelper.updateNavigatorSettingsPreset(navigatorSettingsCombo, navigatorSettings);
        }
        return navigatorSettings;
    }
    public static CSmartCombo<DBPConnectionType> createConnectionTypeCombo(Composite composite) {
        UIUtils.createControlLabel(composite, CoreMessages.dialog_connection_wizard_final_label_connection_type);

        Composite ctGroup = UIUtils.createComposite(composite, 1);

        CSmartCombo<DBPConnectionType> connectionTypeCombo = new CSmartCombo<>(ctGroup, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY, new ConnectionTypeLabelProvider());
        loadConnectionTypes(connectionTypeCombo);
        setConnectionType(connectionTypeCombo, DBPConnectionType.getDefaultConnectionType());
        connectionTypeCombo.select(DBPConnectionType.getDefaultConnectionType());
        final GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
        gd.widthHint = UIUtils.getFontHeight(connectionTypeCombo) * 20;
        connectionTypeCombo.setLayoutData(gd);

        return connectionTypeCombo;
    }

    public static void setConnectionType(@NotNull CSmartCombo<DBPConnectionType> combo, @NotNull DBPConnectionType connectionType) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            final DBPConnectionType item = combo.getItem(i);
            if (item.getId().equals(connectionType.getId())) {
                combo.select(i);
                return;
            }
        }
    }

    public static void loadConnectionTypes(CSmartCombo <DBPConnectionType> connectionTypeCombo) {
        connectionTypeCombo.removeAll();
        for (DBPConnectionType ct : DataSourceProviderRegistry.getInstance().getConnectionTypes()) {
            connectionTypeCombo.addItem(ct);
        }
    }
}