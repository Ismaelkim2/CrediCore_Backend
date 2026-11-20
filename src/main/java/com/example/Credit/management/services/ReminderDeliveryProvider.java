
package com.example.Credit.management.services;

import com.example.Credit.management.Model.TenantReminder;

public interface ReminderDeliveryProvider {

    /**
     * Sends the reminder through its configured channel.
     *
     * Return normally only when the provider confirms acceptance.
     * Throw an exception if delivery cannot be initiated.
     */
    void send(TenantReminder reminder) throws Exception;
}
