package com.example.Credit.management.Model;

import jakarta.mail.FetchProfile;
import lombok.Data;

import java.util.List;


@Data
public class CallbackMetadata {

    private List<Item> Item;
}